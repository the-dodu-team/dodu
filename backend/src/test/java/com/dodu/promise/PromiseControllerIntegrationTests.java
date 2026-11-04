package com.dodu.promise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(PromiseControllerIntegrationTests.ContainerConfiguration.class)
class PromiseControllerIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SecretHasher secretHasher;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.dodu.participant.service.ParticipantEntryService entryService;
    @Autowired
    private PromiseLifecycleService lifecycle;

    private MockMvc mockMvc;
    private String authorization;
    private String scheduledAt;
    private UUID participantId;
    private UUID policyId;

    @Test
    void sameCodeRetainsSavedPromiseAfterLogoutAndSessionReplacement() throws Exception {
        Instant now = Instant.now();
        jdbc.update("update study_period set access_ends_at = ? where id = (select study_period_id from participant where id = ?)",
                timestamp(now.plusSeconds(172800)), participantId);
        String code = com.dodu.participant.domain.AccessCode.generate(new SecureRandom());
        jdbc.update("insert into access_credential(participant_id, secret_hash, issued_at) values (?, ?, ?)",
                participantId, secretHasher.hash(code), timestamp(now));
        String device = "synthetic-device-aaaaaaaaaaaa";
        var first = entryService.enter(code, device);
        authorization = "Bearer " + first.sessionToken();
        String body = "{\"taskTitle\":\"reentry task\",\"toolType\":\"BOOK\",\"scheduledAt\":\"" + scheduledAt + "\"}";
        String id = extractPromiseId(createWithKey(body, "reentry-" + UUID.randomUUID()));
        for (String nextDevice : new String[] {device, "synthetic-device-bbbbbbbbbbbb"}) {
            mockMvc.perform(delete("/api/participant/notification-subscription").header("Authorization", authorization))
                    .andExpect(status().isOk());
            var entered = entryService.enter(code, nextDevice);
            authorization = "Bearer " + entered.sessionToken();
            mockMvc.perform(get("/api/promises/active").header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.promiseId").value(id))
                    .andExpect(jsonPath("$.taskTitle").value("reentry task"));
            assertEquals(1, jdbc.queryForObject("select count(*) from promise where participant_id = ?", Integer.class, participantId));
            assertEquals("Scheduled", jdbc.queryForObject("select lifecycle_status from promise where id = ?", String.class, UUID.fromString(id)));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = SessionToken.generate(new SecureRandom());
        String tokenHash = secretHasher.hash(token);
        UUID studyId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        scheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).withNano(0).toString();

        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "integration-" + suffix, timestamp(now.plusSeconds(172_800)));
        jdbc.update("insert into policy_version(version, effective_from) values (?, ?)",
                "integration-" + suffix, timestamp(now.minusSeconds(60)));
        policyId = jdbc.queryForObject("select id from policy_version where version = ?", UUID.class,
                "integration-" + suffix);
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) "
                        + "values (?, ?, ?, 'Active', ?)", participantId, studyId, "integration-" + suffix, timestamp(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) "
                        + "values (?, ?, ?, 'Active', ?, ?)", deviceId, participantId, "device-" + suffix, timestamp(now), timestamp(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) "
                        + "values (?, ?, ?, ?, 'Active', ?, ?, ?)", sessionId, participantId, deviceId, tokenHash,
                timestamp(now), timestamp(now), timestamp(now.plusSeconds(3_600)));
        authorization = "Bearer " + token;
    }

    @Test
    void sameKeyReturnsTheSamePromiseAndDifferentBodyConflicts() throws Exception {
        String first = "{\"taskTitle\":\"integration task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        String different = "{\"taskTitle\":\"different task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";

        String firstResponse = mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "create-integration-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promiseId").exists())
                .andReturn().getResponse().getContentAsString();
        String promiseId = firstResponse.replaceFirst(".*\\\"promiseId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "create-integration-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promiseId").value(promiseId));

        mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "create-integration-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(different))
                .andExpect(status().isConflict());
    }

    @Test
    void differentKeyCannotCreateSecondActivePromise() throws Exception {
        String body = "{\"taskTitle\":\"active task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        createWithKey(body, "create-active-1");

        mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "create-active-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROMISE_ACTIVE_EXISTS"));
    }

    @Test
    void completedPromiseFreesTheSlotForAnotherPromiseOnSameKstDate() throws Exception {
        String body = "{\"taskTitle\":\"repeat task\",\"toolType\":\"BOOK\",\"scheduledAt\":\"" + scheduledAt + "\"}";
        String first = extractPromiseId(createWithKey(body, "repeat-1"));
        jdbc.update("update promise set lifecycle_status = 'AuthCompleted', final_outcome = 'AuthCompleted' where id = ?", UUID.fromString(first));
        String second = extractPromiseId(createWithKey(body, "repeat-2"));
        assertTrue(!first.equals(second));
        assertEquals(2, jdbc.queryForObject("select count(*) from promise where participant_id = ?", Integer.class, participantId));
        assertEquals(1, jdbc.queryForObject("select count(*) from promise where participant_id = ? and lifecycle_status = 'Scheduled'", Integer.class, participantId));
    }

    @Test
    void concurrentDifferentRequestsStillHaveOnlyOneActiveSlot() throws Exception {
        String body = "{\"taskTitle\":\"slot task\",\"toolType\":\"BOOK\",\"scheduledAt\":\"" + scheduledAt + "\"}";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                String key = "slot-" + i;
                futures.add(pool.submit(() -> { gate.await(); return mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(); }));
            }
            gate.countDown();
            var results = java.util.List.of(futures.get(0).get(), futures.get(1).get());
            assertTrue(results.contains(200)); assertTrue(results.contains(409));
            assertEquals(1, jdbc.queryForObject("select count(*) from promise where participant_id = ?", Integer.class, participantId));
        }
    }

    @Test
    void expiredUnansweredPromiseIsPersistedThenNextPromiseCanBeCreated() throws Exception {
        UUID expired = insertHistoryPromise("expired slot", Instant.now().minusSeconds(3600), "Scheduled", null);
        String body = "{\"taskTitle\":\"next task\",\"toolType\":\"BOOK\",\"scheduledAt\":\"" + scheduledAt + "\"}";
        createWithKey(body, "after-expiry");
        assertEquals("AuthIncomplete", jdbc.queryForObject("select lifecycle_status from promise where id = ?", String.class, expired));
        assertEquals(1, jdbc.queryForObject("select count(*) from validation_survey where promise_id = ?", Integer.class, expired));
        lifecycle.reconcile(participantId);
        assertEquals(1, jdbc.queryForObject("select count(*) from validation_survey where promise_id = ?", Integer.class, expired));
    }

    @Test
    void legacyLowercaseToolIsReadableInActiveAndFinalHistory() throws Exception {
        UUID old = insertHistoryPromise("legacy laptop", Instant.now().minusSeconds(3600), "AuthCompleted", "AuthCompleted");
        jdbc.update("update promise set tool_type = 'laptop' where id = ?", old);
        mockMvc.perform(get("/api/promises/history").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].toolType").value("COMPUTER"));
    }

    @Test
    void resetPreservesParticipantsCredentialsAndUnrelatedEnrollmentEvents() throws Exception {
        String code = com.dodu.participant.domain.AccessCode.generate(new SecureRandom());
        jdbc.update("insert into access_credential(participant_id, secret_hash, issued_at) values (?, ?, now())", participantId, secretHasher.hash(code));
        insertHistoryPromise("cleanup", Instant.now().minusSeconds(3600), "AuthCompleted", "AuthCompleted");
        long participants = jdbc.queryForObject("select count(*) from participant", Long.class);
        long credentials = jdbc.queryForObject("select count(*) from access_credential", Long.class);
        var reset = context.getBean(com.dodu.operator.service.OperatorTestDataResetService.class);
        var result = reset.reset();
        assertEquals(participants, result.participantsKept());
        assertEquals(credentials, result.credentialsKept());
        assertEquals(participants, jdbc.queryForObject("select count(*) from participant", Long.class));
        assertEquals(credentials, jdbc.queryForObject("select count(*) from access_credential", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from promise", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from validation_survey", Integer.class));
    }

    @Test
    void resetStopsBeforeDatabaseDeletionIfStorageFails() {
        UUID promise = insertHistoryPromise("keep on storage error", Instant.now().minusSeconds(3600), "AuthCompleted", "AuthCompleted");
        UUID asset = UUID.randomUUID();
        jdbc.update("insert into photo_asset(id, storage_key, content_type, byte_size, sha256_hash, received_at) values (?, ?, 'image/png', 1, ?, now())",
                asset, "synthetic-reset-" + asset, "a".repeat(64));
        var writer = new com.dodu.intervention.PhotoObjectWriter() {
            public String write(String key, String type, byte[] data) { throw new UnsupportedOperationException(); }
            public void delete(String key) { throw new IllegalStateException("synthetic storage failure"); }
        };
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory(java.util.Map.of("writer", writer));
        var reset = new com.dodu.operator.service.OperatorTestDataResetService(jdbc, factory.getBeanProvider(com.dodu.intervention.PhotoObjectWriter.class));
        var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> tx.execute(s -> reset.reset()));
        assertEquals(1, jdbc.queryForObject("select count(*) from promise where id = ?", Integer.class, promise));
        assertEquals(1, jdbc.queryForObject("select count(*) from photo_asset where id = ?", Integer.class, asset));
        jdbc.update("delete from photo_asset where id = ?", asset);
    }

    @Test
    void resetDeletesReferencedPhotoObjectsBeforeRemovingMetadata() {
        UUID asset = UUID.randomUUID();
        jdbc.update("insert into photo_asset(id, storage_key, content_type, byte_size, sha256_hash, received_at) values (?, ?, 'image/png', 1, ?, now())",
                asset, "synthetic-reset-" + asset, "b".repeat(64));
        var deleted = new java.util.concurrent.atomic.AtomicInteger();
        var writer = new com.dodu.intervention.PhotoObjectWriter() {
            public String write(String key, String type, byte[] data) { throw new UnsupportedOperationException(); }
            public void delete(String key) {
                assertEquals(1, jdbc.queryForObject("select count(*) from photo_asset where id = ?", Integer.class, asset));
                deleted.incrementAndGet();
            }
        };
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory(java.util.Map.of("writer", writer));
        var reset = new com.dodu.operator.service.OperatorTestDataResetService(jdbc, factory.getBeanProvider(com.dodu.intervention.PhotoObjectWriter.class));
        var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var result = tx.execute(s -> reset.reset());
        assertEquals(1, deleted.get()); assertEquals(1, result.photos());
        assertEquals(0, jdbc.queryForObject("select count(*) from photo_asset", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from participant where id = ?", Integer.class, participantId));
    }

    @Test
    void concurrentSameKeyRequestsCreateOnePromise() throws Exception {
        String body = "{\"taskTitle\":\"concurrent task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> createWithKey(body, "create-concurrent-1"));
            Future<String> second = executor.submit(() -> createWithKey(body, "create-concurrent-1"));
            String firstId = extractPromiseId(first.get());
            String secondId = extractPromiseId(second.get());
            assertEquals(firstId, secondId);
            assertEquals(1, jdbc.queryForObject("select count(*) from promise_creation_request where idempotency_key = ?", Integer.class,
                    "create-concurrent-1"));
            assertEquals(1, jdbc.queryForObject("select count(*) from promise where task_title = ?", Integer.class,
                    "concurrent task"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReschedulesUseOptimisticVersionAndRecordNewDate() throws Exception {
        String body = "{\"taskTitle\":\"reschedule task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        String promiseId = extractPromiseId(createWithKey(body, "create-reschedule-1"));
        String nextScheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(26).withNano(0).toString();
        String reschedule = "{\"scheduledAt\":\"" + nextScheduledAt + "\",\"expectedVersion\":0}";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<org.springframework.test.web.servlet.MvcResult> first = executor.submit(() -> reschedule(promiseId, reschedule));
            Future<org.springframework.test.web.servlet.MvcResult> second = executor.submit(() -> reschedule(promiseId, reschedule));
            var firstResult = first.get();
            var secondResult = second.get();
            int firstStatus = firstResult.getResponse().getStatus();
            int secondStatus = secondResult.getResponse().getStatus();
            if (firstStatus != 200 && secondStatus != 200) {
                throw new AssertionError(firstResult.getResponse().getContentAsString() + " / "
                        + secondResult.getResponse().getContentAsString());
            }
            assertTrue((firstStatus == 200 && secondStatus == 409) || (firstStatus == 409 && secondStatus == 200));
            var nextDate = OffsetDateTime.parse(nextScheduledAt).toInstant().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
            assertEquals(1, jdbc.queryForObject("""
                    select count(*)
                      from promise_date_usage
                     where participant_id = (select participant_id from promise where id = ?)
                       and scheduled_date_kst = ?
                    """, Integer.class, UUID.fromString(promiseId), nextDate));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCancelsCloseOnlyOnce() throws Exception {
        String body = "{\"taskTitle\":\"cancel task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        String promiseId = extractPromiseId(createWithKey(body, "create-cancel-1"));
        String cancel = "{\"expectedVersion\":0}";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> cancel(promiseId, cancel));
            Future<Integer> second = executor.submit(() -> cancel(promiseId, cancel));
            int firstStatus = first.get();
            int secondStatus = second.get();
            assertTrue((firstStatus == 200 && secondStatus == 409) || (firstStatus == 409 && secondStatus == 200));
            assertEquals(1, jdbc.queryForObject("select count(*) from promise where lifecycle_status = 'Closed' and final_outcome = 'Cancelled'", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void secondRescheduleIsRejectedAfterTheSingleAllowedChange() throws Exception {
        String body = "{\"taskTitle\":\"single reschedule task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        String promiseId = extractPromiseId(createWithKey(body, "create-single-reschedule-1"));
        String firstScheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(26).withNano(0).toString();
        String secondScheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(27).withNano(0).toString();

        mockMvc.perform(patch("/api/promises/" + promiseId + "/schedule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + firstScheduledAt + "\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/promises/" + promiseId + "/schedule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + secondScheduledAt + "\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROMISE_RESCHEDULE_LIMIT_REACHED"));
    }

    @Test
    void cancellationAfterScheduledStartIsRejected() throws Exception {
        String body = "{\"taskTitle\":\"started cancel task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt + "\"}";
        String promiseId = extractPromiseId(createWithKey(body, "create-started-cancel-1"));
        jdbc.update("update promise set scheduled_at = ?, scheduled_date_kst = ? where id = ?",
                timestamp(Instant.now().minusSeconds(60)),
                Instant.now().minusSeconds(60).atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                UUID.fromString(promiseId));

        mockMvc.perform(post("/api/promises/" + promiseId + "/cancel")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROMISE_NOT_MODIFIABLE"));
    }

    @Test
    void historyReturnsFinalizedPromisesAndExpiredUnansweredPromise() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID verifiedId = insertHistoryPromise("verified", now.minusSeconds(7_200), "AuthCompleted", null);
        UUID cancelledId = insertHistoryPromise("cancelled", now.minusSeconds(3_600), "Closed", "Cancelled");
        UUID expiredId = insertHistoryPromise("expired", now.minusSeconds(1_800), "Scheduled", null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/promises/history")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promiseId").value(expiredId.toString()))
                .andExpect(jsonPath("$[0].lifecycleStatus").value("AuthIncomplete"))
                .andExpect(jsonPath("$[0].finalOutcome").value("AuthIncomplete"))
                .andExpect(jsonPath("$[1].promiseId").value(cancelledId.toString()))
                .andExpect(jsonPath("$[1].finalOutcome").value("Cancelled"))
                .andExpect(jsonPath("$[2].promiseId").value(verifiedId.toString()))
                .andExpect(jsonPath("$[2].lifecycleStatus").value("AuthCompleted"));
    }

    @Test
    void expiredScheduledHistoryDoesNotMisclassifyAcceptedOrTechnicalAttempts() throws Exception {
        Instant now = Instant.now();
        for (String processing : new String[] {"Evaluating", "Completed", "TechFailed"}) {
            UUID id = insertHistoryPromise("excluded " + processing, now.minusSeconds(3600), "Scheduled", null);
            jdbc.update("insert into auth_attempt(promise_id, client_request_id, request_fingerprint, tool_type, processing_status, request_received_at, valid_received_at) values (?, ?, ?, 'BOOK', ?, ?, ?)",
                    id, UUID.randomUUID(), "synthetic-" + processing, processing,
                    timestamp(now.minusSeconds(3500)), timestamp(now.minusSeconds(3500)));
        }
        mockMvc.perform(get("/api/promises/history").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertEquals(3, jdbc.queryForObject("select count(*) from promise where participant_id = ? and lifecycle_status = 'Scheduled'", Integer.class, participantId));
    }

    private UUID insertHistoryPromise(String title, Instant scheduled, String lifecycle, String outcome) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, final_outcome, auth_window_ends_at) values (?, ?, ?, ?, ?, ?, 'BOOK', 'NORMAL', ?, ?, ?)",
                id, participantId, policyId, title, timestamp(scheduled), scheduled.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), lifecycle, outcome, timestamp(scheduled.plusSeconds(1_800)));
        return id;
    }

    private String createWithKey(String body, String key) throws Exception {
        return mockMvc.perform(post("/api/promises")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.MvcResult reschedule(String promiseId, String body) throws Exception {
        return mockMvc.perform(patch("/api/promises/" + promiseId + "/schedule")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private int cancel(String promiseId, String body) throws Exception {
        return mockMvc.perform(post("/api/promises/" + promiseId + "/cancel")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private static String extractPromiseId(String response) {
        return response.replaceFirst(".*\\\"promiseId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer("postgres:16-alpine");
        }
    }
}
