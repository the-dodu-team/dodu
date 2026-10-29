package com.dodu.promise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.dodu.access.AccessCredentialHasher;
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
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String authorization;
    private String scheduledAt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = "integration-token-" + suffix;
        String tokenHash = new AccessCredentialHasher("local-development-only").hash(token);
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        scheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).withNano(0).toString();

        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "integration-" + suffix, timestamp(now.plusSeconds(172_800)));
        jdbc.update("insert into policy_version(version, effective_from) values (?, ?)",
                "integration-" + suffix, timestamp(now.minusSeconds(60)));
        UUID policyId = jdbc.queryForObject("select id from policy_version where version = ?", UUID.class,
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
