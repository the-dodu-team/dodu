package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.dodu.access.AccessCredentialHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(PhotoEvaluationRecoveryWorkerIntegrationTests.ContainerConfiguration.class)
class PhotoEvaluationRecoveryWorkerIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PhotoEvaluationRecoveryWorker worker;
    @Autowired private FakeRecoveryHandler handler;

    @BeforeEach
    void resetHandler() {
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("WORKER_TIMEOUT"));
    }

    @Test
    void retryIsPersistedWithBackoffThenSuccessfulRecoveryUpdatesAttemptAndPromise() {
        Fixture fixture = fixture(0);

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("READY", status(fixture.jobId()));
        assertEquals(1, attempts(fixture.jobId()));
        assertEquals(NOW.plusSeconds(30), nextAttempt(fixture.jobId()));

        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("model-test"));
        assertEquals(1, worker.processDueJobs(NOW.plusSeconds(30)));
        assertEquals("SUCCEEDED", status(fixture.jobId()));
        assertEquals("Completed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("AuthCompleted", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
    }

    @Test
    void thirdFailureExhaustsJobAndClosesPromiseAsTechnicalFailure() {
        Fixture fixture = fixture(2);

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("EXHAUSTED", status(fixture.jobId()));
        assertEquals("TechFailed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("TechFailed", value("select final_outcome from promise where id = ?", fixture.promiseId()));
    }

    private Fixture fixture(int attemptCount) {
        String suffix = UUID.randomUUID().toString();
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "recovery-study-" + suffix, Timestamp.from(NOW.plusSeconds(3_600)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "recovery-policy-" + suffix, Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "recovery-" + suffix, Timestamp.from(NOW.minusSeconds(600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, 'recovery task', ?, ?, 'laptop', 'NORMAL', 'Scheduled', ?)",
                promiseId, participantId, policyId, Timestamp.from(NOW.minusSeconds(120)),
                NOW.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), Timestamp.from(NOW.plusSeconds(1_680)));
        jdbc.update("insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type, processing_status, request_received_at, valid_received_at) values (?, ?, ?, 'recovery-fingerprint', 'laptop', 'Evaluating', ?, ?)",
                attemptId, promiseId, UUID.randomUUID(), Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("insert into photo_evaluation_job(id, auth_attempt_id, promise_id, lifecycle_status, attempt_count, next_attempt_at) values (?, ?, ?, 'READY', ?, ?)",
                jobId, attemptId, promiseId, attemptCount, Timestamp.from(NOW));
        return new Fixture(jobId, attemptId, promiseId);
    }

    private String status(UUID jobId) {
        return value("select lifecycle_status from photo_evaluation_job where id = ?", jobId);
    }

    private int attempts(UUID jobId) {
        return jdbc.queryForObject("select attempt_count from photo_evaluation_job where id = ?", Integer.class, jobId);
    }

    private Instant nextAttempt(UUID jobId) {
        return jdbc.queryForObject("select next_attempt_at from photo_evaluation_job where id = ?", (rs, rowNum) -> rs.getTimestamp(1).toInstant(), jobId);
    }

    private String value(String sql, UUID id) {
        return jdbc.queryForObject(sql, String.class, id);
    }

    private record Fixture(UUID jobId, UUID attemptId, UUID promiseId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }

        @Bean @Primary
        FakeRecoveryHandler recoveryHandler() { return new FakeRecoveryHandler(); }
    }

    static final class FakeRecoveryHandler implements PhotoEvaluationRecoveryHandler {
        private final AtomicReference<RecoveryResult> next = new AtomicReference<>();

        @Override
        public RecoveryResult recover(RecoveryJob job) {
            return next.get();
        }
    }
}
