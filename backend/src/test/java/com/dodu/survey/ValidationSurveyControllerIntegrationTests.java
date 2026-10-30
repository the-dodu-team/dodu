package com.dodu.survey;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(ValidationSurveyControllerIntegrationTests.ContainerConfiguration.class)
class ValidationSurveyControllerIntegrationTests {
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
        String token = "S".repeat(11) + suffix.replace("-", "");
        Instant now = Instant.now();
        UUID studyId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        promiseId = UUID.randomUUID();
        jdbc.update("insert into study_period(id, code, access_ends_at, intervention_ends_at) values (?, ?, ?, ?)",
                studyId, "survey-study-" + suffix, Timestamp.from(now.plusSeconds(86_400)), Timestamp.from(now.plusSeconds(86_400)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "survey-policy-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "survey-participant-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                deviceId, participantId, "survey-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                sessionId, participantId, deviceId, new SecretHasher(PEPPER).hash(token), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        Instant scheduledAt = now.minusSeconds(600);
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, 'survey task', ?, ?, 'laptop', 'NORMAL', 'AuthCompleted', ?)",
                promiseId, participantId, policyId, Timestamp.from(scheduledAt), scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), Timestamp.from(now));
        jdbc.update("insert into validation_survey(promise_id, questionnaire_version, lifecycle_status, eligibility_status, available_at, expires_at) values (?, 'v2.5.2', 'Pending', 'AuthCompleted', ?, ?)",
                promiseId, Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3_600)));
        authorization = "Bearer " + token;
    }

    @Test
    void returnsOwnedSurveyWithoutQuestionnaireContent() throws Exception {
        mockMvc.perform(get("/api/promises/" + promiseId + "/validation-survey").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Prompted"))
                .andExpect(jsonPath("$.eligibilityStatus").value("AuthCompleted"))
                .andExpect(jsonPath("$.questionnaire").doesNotExist())
                .andExpect(jsonPath("$.rawAnswers").doesNotExist());
    }

    @Test
    void doesNotReturnAnotherParticipantsSurvey() throws Exception {
        String otherToken = "O".repeat(11) + UUID.randomUUID().toString().replace("-", "");
        UUID otherParticipant = UUID.randomUUID();
        UUID otherDevice = UUID.randomUUID();
        UUID otherSession = UUID.randomUUID();
        UUID studyId = jdbc.queryForObject("select study_period_id from participant where id = (select participant_id from promise where id = ?)", UUID.class, promiseId);
        Instant now = Instant.now();
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                otherParticipant, studyId, "other-survey-" + UUID.randomUUID(), Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                otherDevice, otherParticipant, "other-survey-device-" + UUID.randomUUID(), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                otherSession, otherParticipant, otherDevice, new SecretHasher(PEPPER).hash(otherToken), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));

        mockMvc.perform(get("/api/promises/" + promiseId + "/validation-survey")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
