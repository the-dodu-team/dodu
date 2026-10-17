package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.dodu.access.AccessCredentialHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(PhotoEvaluationResultControllerIntegrationTests.ContainerConfiguration.class)
class PhotoEvaluationResultControllerIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String authorization;
    private UUID promiseId;
    private UUID attemptId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = "result-token-" + suffix;
        String tokenHash = new AccessCredentialHasher("local-development-only").hash(token);
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        promiseId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into study_period(id, code) values (?, ?)", studyId, "result-study-" + suffix);
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)", policyId, "result-policy-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)", participantId, studyId, "result-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)", deviceId, participantId, "result-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)", sessionId, participantId, deviceId, tokenHash, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status) values (?, ?, ?, 'result task', ?, ?, 'laptop', 'NORMAL', 'Evaluating')", promiseId, participantId, policyId, Timestamp.from(now.minusSeconds(300)), now.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate());
        jdbc.update("insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type, processing_status, request_received_at) values (?, ?, ?, 'result-fingerprint', 'laptop', 'Evaluating', ?)", attemptId, promiseId, UUID.randomUUID(), Timestamp.from(now.minusSeconds(120)));
        authorization = "Bearer " + token;
    }

    @Test
    void completedResultMarksPromiseAuthCompleted() throws Exception {
        mockMvc.perform(post("/api/promises/" + promiseId + "/photo-attempts/" + attemptId + "/result")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\",\"modelVersion\":\"test-model\"}"))
                .andExpect(status().isOk());
        assertEquals("AuthCompleted", jdbc.queryForObject("select lifecycle_status from promise where id = ?", String.class, promiseId));
    }

    @Test
    void technicalErrorClosesPromiseAsTechFailed() throws Exception {
        mockMvc.perform(post("/api/promises/" + promiseId + "/photo-attempts/" + attemptId + "/result")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"TECH_ERROR\",\"technicalErrorType\":\"WORKER_TIMEOUT\"}"))
                .andExpect(status().isOk());
        assertEquals("TechFailed", jdbc.queryForObject("select final_outcome from promise where id = ?", String.class, promiseId));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
