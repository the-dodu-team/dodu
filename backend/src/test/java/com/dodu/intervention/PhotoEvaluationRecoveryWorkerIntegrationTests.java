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
        handler.during.set(null);
    }

    @Test
    void staleCompletionDoesNotOverwriteResultSavedDuringAiCall() {
        Fixture fixture = fixture(0);
        // AI 호출 중 결과 API가 같은 attempt를 기술 실패로 먼저 확정한 상황.
        handler.during.set(() -> {
            jdbc.update("update auth_attempt set processing_status = 'TechFailed', decision_result = 'TechFailed' where id = ?", fixture.attemptId());
            jdbc.update("update promise set lifecycle_status = 'Closed', final_outcome = 'TechFailed' where id = ?", fixture.promiseId());
        });
        // SCRUM-84 이후 완료는 label이 있어야 승인 대상이다(label 없는 완료는 재시도).
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("laptop", "model-test"));

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("TechFailed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("Closed", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals("TechFailed", value("select final_outcome from promise where id = ?", fixture.promiseId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from validation_survey where promise_id = ?", Integer.class, fixture.promiseId()));
        assertEquals("SKIPPED", status(fixture.jobId()));
    }

    @Test
    void staleExhaustionDoesNotClosePromiseWhoseAttemptWasAlreadyDecided() {
        Fixture fixture = fixture(2);
        // AI 호출 중 결과 API가 같은 attempt를 거절로 먼저 확정한 상황(재촬영 가능해야 함).
        handler.during.set(() -> jdbc.update(
                "update auth_attempt set processing_status = 'Rejected', decision_result = 'Rejected' where id = ?", fixture.attemptId()));

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("Rejected", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("Scheduled", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals(null, value("select final_outcome from promise where id = ?", fixture.promiseId()));
        assertEquals("SKIPPED", status(fixture.jobId()));
    }

    @Test
    void retryIsPersistedWithBackoffThenSuccessfulRecoveryUpdatesAttemptAndPromise() {
        Fixture fixture = fixture(0);

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("READY", status(fixture.jobId()));
        assertEquals(1, attempts(fixture.jobId()));
        assertEquals(NOW.plusSeconds(30), nextAttempt(fixture.jobId()));

        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("laptop", "model-test"));
        assertEquals(1, worker.processDueJobs(NOW.plusSeconds(30)));
        assertEquals("SUCCEEDED", status(fixture.jobId()));
        assertEquals("Completed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("AuthCompleted", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        // R-36: 복구 경로로 인증 완료돼도 실제 시작 자기보고 설문이 생성된다.
        assertEquals("AuthCompleted", value("select eligibility_status from validation_survey where promise_id = ?", fixture.promiseId()));
    }

    @Test
    void thirdFailureExhaustsJobAndClosesPromiseAsTechnicalFailure() {
        Fixture fixture = fixture(2);

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("EXHAUSTED", status(fixture.jobId()));
        assertEquals("TechFailed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("TechFailed", value("select final_outcome from promise where id = ?", fixture.promiseId()));
    }

    @Test
    void laptopLabelOnBookPromiseIsRejectedAndPromiseStaysOpenForResubmission() {
        Fixture fixture = fixture(0);
        jdbc.update("update promise set tool_type = 'BOOK' where id = ?", fixture.promiseId());
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("laptop", "model-test"));

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("SUCCEEDED", status(fixture.jobId()));
        assertEquals("Rejected", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("AI_TOOL_MISMATCH", value("select rejection_reason from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("Scheduled", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from validation_survey where promise_id = ?", Integer.class, fixture.promiseId()));
    }

    @Test
    void bookLabelOnBookPromiseCompletes() {
        Fixture fixture = fixture(0);
        jdbc.update("update promise set tool_type = 'BOOK' where id = ?", fixture.promiseId());
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("book", "model-test"));

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("Completed", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("AuthCompleted", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
    }

    @Test
    void completedWithoutLabelIsRetriedInsteadOfApproved() {
        Fixture fixture = fixture(0);
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("model-test"));

        assertEquals(1, worker.processDueJobs(NOW));
        assertEquals("READY", status(fixture.jobId()));
        assertEquals("Evaluating", value("select processing_status from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("Scheduled", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals("AI_LABEL_MISSING", value("select last_error_code from photo_evaluation_job where id = ?", fixture.jobId()));
        // 같은 DB를 쓰는 다른 테스트가 이 재시도 작업을 처리하지 않도록 정리한다.
        jdbc.update("update photo_evaluation_job set lifecycle_status = 'SKIPPED' where id = ?", fixture.jobId());
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
        private final AtomicReference<Runnable> during = new AtomicReference<>();

        @Override
        public RecoveryResult recover(RecoveryJob job) {
            Runnable concurrentChange = during.get();
            if (concurrentChange != null) concurrentChange.run();
            return next.get();
        }
    }
}
