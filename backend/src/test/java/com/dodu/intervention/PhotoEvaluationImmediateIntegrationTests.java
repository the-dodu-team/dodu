package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 사진 제출 커밋 직후 판정 경로. 주기 워커는 1시간으로 늦춰 두어, 판정이 끝났다면 즉시 경로가 처리한 것이다.
 */
@SpringBootTest(properties = "dodu.photo-recovery.worker-delay-ms=3600000")
@Import(PhotoEvaluationImmediateIntegrationTests.ContainerConfiguration.class)
class PhotoEvaluationImmediateIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SecretHasher secretHasher;
    @Autowired private PhotoEvaluationRecoveryWorker worker;
    @Autowired private ControlledHandler handler;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetHandler() {
        // 앞 테스트가 남긴 대기 작업이 워커 실행 결과에 섞이지 않게 정리한다.
        jdbc.update("update photo_evaluation_job set lifecycle_status = 'SKIPPED' where lifecycle_status in ('READY', 'PROCESSING')");
        handler.calls.set(0);
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("laptop", "model-test"));
        handler.entered = new CountDownLatch(1);
        handler.release = null;
    }

    @Test
    void immediateExecutorDoesNotReplaceDefaultApplicationTaskExecutor() {
        // FCM이 켜진 운영에서 DeviceChangeNotificationListener가 이 빈을 주입받는다. 즉시 판정 실행기를 빈으로 등록하면
        // Spring Boot가 기본 실행기를 만들지 않아 서버가 시작하지 못했다(Render 배포 실패, 2026-11-22).
        assertTrue(context.containsBean("applicationTaskExecutor"));
        assertTrue(context.getBean("applicationTaskExecutor") instanceof org.springframework.core.task.TaskExecutor);
    }

    @Test
    void submittedPhotoIsEvaluatedRightAfterCommitWithoutWaitingForWorker() throws Exception {
        Session session = session();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        long started = System.nanoTime();
        mockMvc.perform(multipart("/api/promises/" + session.promiseId() + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {4, 5, 6}))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                        .header("Authorization", session.authorization()))
                .andExpect(status().isOk());

        UUID attemptId = jdbc.queryForObject("select id from auth_attempt where promise_id = ?", UUID.class, session.promiseId());
        assertTrue(waitUntil(() -> "Completed".equals(attemptStatus(attemptId))), "즉시 경로로 판정되지 않음");
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10));
        assertEquals("AuthCompleted", value("select lifecycle_status from promise where id = ?", session.promiseId()));
        assertEquals("SUCCEEDED", value("select lifecycle_status from photo_evaluation_job where auth_attempt_id = ?", attemptId));
        assertEquals(1, handler.calls.get());
    }

    @Test
    void rolledBackSubmissionDoesNotStartEvaluation() throws Exception {
        Fixture fixture = fixture();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            events.publishEvent(new PhotoEvaluationRequested(fixture.attemptId()));
            tx.setRollbackOnly();
        });

        assertFalse(handler.entered.await(1, TimeUnit.SECONDS));
        assertEquals(0, handler.calls.get());
        assertEquals("READY", jobStatus(fixture.jobId()));
    }

    @Test
    void workerDoesNotEvaluateJobAlreadyClaimedByImmediatePath() throws Exception {
        Fixture fixture = fixture();
        handler.release = new CountDownLatch(1);
        CompletableFuture<Boolean> immediate = CompletableFuture.supplyAsync(() -> worker.processNow(fixture.attemptId()));
        assertTrue(handler.entered.await(5, TimeUnit.SECONDS));

        // 즉시 경로가 AI를 기다리는 동안 워커가 돌아도 같은 작업을 다시 잡지 않는다.
        assertEquals(0, worker.processDueJobs(Instant.now()));
        handler.release.countDown();

        assertTrue(immediate.get(5, TimeUnit.SECONDS));
        assertEquals(1, handler.calls.get());
        assertEquals("Completed", attemptStatus(fixture.attemptId()));
        assertEquals("SUCCEEDED", jobStatus(fixture.jobId()));
        assertEquals(1, jdbc.queryForObject("select attempt_count from photo_evaluation_job where id = ?", Integer.class, fixture.jobId()));
    }

    @Test
    void immediatePathSkipsJobAlreadyProcessedByWorker() {
        Fixture fixture = fixture();
        assertEquals(1, worker.processDueJobs(Instant.now()));

        assertFalse(worker.processNow(fixture.attemptId()));
        assertEquals(1, handler.calls.get());
        assertEquals("SUCCEEDED", jobStatus(fixture.jobId()));
    }

    @Test
    void resultArrivingAfterLeaseWasTakenOverIsDropped() throws Exception {
        Fixture fixture = fixture();
        handler.release = new CountDownLatch(1);
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.completed("laptop", "slow-model"));
        CompletableFuture<Boolean> slow = CompletableFuture.supplyAsync(() -> worker.processNow(fixture.attemptId()));
        assertTrue(handler.entered.await(5, TimeUnit.SECONDS));

        // 첫 호출이 AI를 기다리는 사이 점유 시간(60초)이 지난 것으로 보고 워커가 다시 잡아 거절로 확정한다.
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.rejected("AI_OTHER", "takeover-model"));
        assertEquals(1, worker.processDueJobs(Instant.now().plus(PhotoEvaluationRecoveryWorker.LEASE).plusSeconds(1)));
        handler.release.countDown();

        // 늦게 끝난 첫 호출의 완료 결과는 버려진다.
        assertTrue(slow.get(5, TimeUnit.SECONDS));
        assertEquals("Rejected", attemptStatus(fixture.attemptId()));
        assertEquals("takeover-model", value("select model_version from auth_attempt where id = ?", fixture.attemptId()));
        assertEquals("Scheduled", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals(2, jdbc.queryForObject("select attempt_count from photo_evaluation_job where id = ?", Integer.class, fixture.jobId()));
    }

    @Test
    void resultArrivingAfterParticipantWithdrewIsNotSaved() throws Exception {
        Fixture fixture = fixture();
        handler.release = new CountDownLatch(1);
        CompletableFuture<Boolean> slow = CompletableFuture.supplyAsync(() -> worker.processNow(fixture.attemptId()));
        assertTrue(handler.entered.await(5, TimeUnit.SECONDS));

        // AI를 기다리는 사이 참가자가 철회한다.
        jdbc.update("""
                update participant set lifecycle_status = 'Withdrawn', withdrawn_at = now()
                 where id = (select participant_id from promise where id = ?)
                """, fixture.promiseId());
        handler.release.countDown();

        assertTrue(slow.get(5, TimeUnit.SECONDS));
        assertEquals("Evaluating", attemptStatus(fixture.attemptId()));
        assertEquals("Scheduled", value("select lifecycle_status from promise where id = ?", fixture.promiseId()));
        assertEquals("SKIPPED", jobStatus(fixture.jobId()));
        assertEquals("PARTICIPANT_WITHDRAWN", value("select last_error_code from photo_evaluation_job where id = ?", fixture.jobId()));
    }

    @Test
    void fullExecutorLeavesJobForWorker() {
        Fixture fixture = fixture();
        PhotoEvaluationImmediateDispatcher saturated = new PhotoEvaluationImmediateDispatcher(worker, task -> {
            throw new RejectedExecutionException("full");
        });

        saturated.afterCommit(new PhotoEvaluationRequested(fixture.attemptId()));
        assertEquals("READY", jobStatus(fixture.jobId()));
        assertEquals(0, handler.calls.get());

        assertEquals(1, worker.processDueJobs(Instant.now()));
        assertEquals("Completed", attemptStatus(fixture.attemptId()));
    }

    @Test
    void retryableFailureOnImmediatePathKeepsBackoffForWorker() {
        Fixture fixture = fixture();
        handler.next.set(PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_REQUEST_FAILED"));

        assertTrue(worker.processNow(fixture.attemptId()));
        assertEquals("READY", jobStatus(fixture.jobId()));
        assertEquals("Evaluating", attemptStatus(fixture.attemptId()));
        assertEquals("AI_REQUEST_FAILED", value("select last_error_code from photo_evaluation_job where id = ?", fixture.jobId()));
        // 재시도 대기 중에는 즉시 경로로 다시 불러도 재시도 간격을 건너뛰지 않는다.
        assertFalse(worker.processNow(fixture.attemptId()));
        assertEquals(1, handler.calls.get());
        jdbc.update("update photo_evaluation_job set lifecycle_status = 'SKIPPED' where id = ?", fixture.jobId());
    }

    private boolean waitUntil(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.get()) return true;
            Thread.sleep(100);
        }
        return condition.get();
    }

    private Fixture fixture() {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "immediate-study-" + suffix, Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "immediate-policy-" + suffix, Timestamp.from(now.minusSeconds(600)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "immediate-" + suffix, Timestamp.from(now.minusSeconds(600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, 'immediate task', ?, ?, 'laptop', 'NORMAL', 'Scheduled', ?)",
                promiseId, participantId, policyId, Timestamp.from(now.minusSeconds(120)),
                now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), Timestamp.from(now.plusSeconds(1_680)));
        jdbc.update("insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type, processing_status, request_received_at, valid_received_at) values (?, ?, ?, 'immediate-fingerprint', 'laptop', 'Evaluating', ?, ?)",
                attemptId, promiseId, UUID.randomUUID(), Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into photo_evaluation_job(id, auth_attempt_id, promise_id, lifecycle_status, attempt_count, next_attempt_at) values (?, ?, ?, 'READY', 0, ?)",
                jobId, attemptId, promiseId, Timestamp.from(now.minusSeconds(1)));
        return new Fixture(jobId, attemptId, promiseId);
    }

    private Session session() {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        String token = SessionToken.generate(new SecureRandom());
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)", studyId, "immediate-api-" + suffix, Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)", policyId, "immediate-api-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)", participantId, studyId, "immediate-api-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)", deviceId, participantId, "immediate-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)", UUID.randomUUID(), participantId, deviceId, secretHasher.hash(token), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status) values (?, ?, ?, 'immediate api task', ?, ?, 'laptop', 'NORMAL', 'Scheduled')", promiseId, participantId, policyId, Timestamp.from(now.minusSeconds(300)), now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
        return new Session("Bearer " + token, promiseId);
    }

    private String attemptStatus(UUID attemptId) {
        return value("select processing_status from auth_attempt where id = ?", attemptId);
    }

    private String jobStatus(UUID jobId) {
        return value("select lifecycle_status from photo_evaluation_job where id = ?", jobId);
    }

    private String value(String sql, UUID id) {
        return jdbc.queryForObject(sql, String.class, id);
    }

    private record Fixture(UUID jobId, UUID attemptId, UUID promiseId) { }
    private record Session(String authorization, UUID promiseId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }

        @Bean @Primary
        ControlledHandler controlledHandler() { return new ControlledHandler(); }
    }

    /** 호출 횟수를 세고, release가 있으면 첫 호출만 늦춰(느린 AI) 경쟁 상황을 만든다. */
    static final class ControlledHandler implements PhotoEvaluationRecoveryHandler {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<RecoveryResult> next = new AtomicReference<>();
        private volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch release;

        @Override
        public RecoveryResult recover(RecoveryJob job) {
            int call = calls.incrementAndGet();
            // AI 응답은 호출 시점 값으로 정한다(늦게 끝나는 호출이 나중에 바뀐 값을 쓰지 않게).
            RecoveryResult result = next.get();
            entered.countDown();
            if (call == 1) await(release);
            return result;
        }

        private static void await(CountDownLatch latch) {
            if (latch == null) return;
            try { latch.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
