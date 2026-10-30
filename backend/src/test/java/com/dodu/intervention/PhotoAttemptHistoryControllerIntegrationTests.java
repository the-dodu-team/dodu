package com.dodu.intervention;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dodu.participant.domain.SecretHasher;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(PhotoAttemptHistoryControllerIntegrationTests.ContainerConfiguration.class)
class PhotoAttemptHistoryControllerIntegrationTests {
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private UUID promiseId;
    private String authorization;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = "H".repeat(11) + suffix.replace("-", "");
        Instant now = Instant.now();
        UUID studyId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        promiseId = UUID.randomUUID();

        jdbc.update("insert into study_period(id, code, access_ends_at, intervention_ends_at) values (?, ?, ?, ?)",
                studyId, "history-study-" + suffix, Timestamp.from(now.plusSeconds(86_400)), Timestamp.from(now.plusSeconds(86_400)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "history-policy-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "history-participant-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                deviceId, participantId, "history-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                sessionId, participantId, deviceId, new SecretHasher(PEPPER).hash(token), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        Instant scheduledAt = now.minusSeconds(600);
        jdbc.update("""
                insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst,
                                    tool_type, creation_type, lifecycle_status, auth_window_ends_at)
                values (?, ?, ?, 'history task', ?, ?, 'laptop', 'NORMAL', 'AuthCompleted', ?)
                """, promiseId, participantId, policyId, Timestamp.from(scheduledAt),
                scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), Timestamp.from(now));
        jdbc.update("""
                insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type,
                                         processing_status, decision_result, model_version, request_received_at,
                                         valid_received_at, decided_at)
                values (?, ?, ?, 'private-fingerprint', 'laptop', 'Completed', 'COMPLETED', 'model-test', ?, ?, ?)
                """, UUID.randomUUID(), promiseId, UUID.randomUUID(), Timestamp.from(scheduledAt),
                Timestamp.from(scheduledAt), Timestamp.from(now));
        authorization = "Bearer " + token;
    }

    @Test
    void returnsOwnedHistoryWithoutPrivateStorageFields() throws Exception {
        mockMvc.perform(get("/api/promises/" + promiseId + "/auth-attempts").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processingStatus").value("Completed"))
                .andExpect(jsonPath("$[0].decisionResult").value("COMPLETED"))
                .andExpect(jsonPath("$[0].modelVersion").value("model-test"))
                .andExpect(jsonPath("$[0].requestFingerprint").doesNotExist())
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
    }

    @Test
    void hidesAnotherParticipantsPromiseAsNotFound() throws Exception {
        String otherToken = "O".repeat(11) + UUID.randomUUID().toString().replace("-", "");
        UUID otherParticipant = UUID.randomUUID();
        UUID otherDevice = UUID.randomUUID();
        UUID otherSession = UUID.randomUUID();
        UUID studyId = jdbc.queryForObject("select study_period_id from participant where id = (select participant_id from promise where id = ?)", UUID.class, promiseId);
        Instant now = Instant.now();
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                otherParticipant, studyId, "other-history-" + UUID.randomUUID(), Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                otherDevice, otherParticipant, "other-history-device-" + UUID.randomUUID(), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                otherSession, otherParticipant, otherDevice, new SecretHasher(PEPPER).hash(otherToken), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));

        mockMvc.perform(get("/api/promises/" + promiseId + "/auth-attempts")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
