package com.dodu.survey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Autowired private com.dodu.participant.service.ParticipantAuthService auth;
    @Autowired private InAppSurveyReminderService reminders;
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
        assertEquals("Pending", jdbc.queryForObject("select lifecycle_status from validation_survey where promise_id = ?", String.class, promiseId));
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey/inapp-reminder")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.show").value(false));
        assertEquals(0, jdbc.queryForObject("select count(*) from validation_survey where promise_id = ? and inapp_reprompted_at is not null", Integer.class, promiseId));
    }

    @Test
    void submitPersistsTimestampsAndReturnsSubmittedSurvey() throws Exception {
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey")
                        .header("Authorization", authorization)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualStarted\":true,\"startOrder\":\"after\",\"startDelayBucket\":\"within5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Submitted"))
                .andExpect(jsonPath("$.actualStarted").value(true))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey")
                        .header("Authorization", authorization)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualStarted\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void skipPersistsTimestampAndIsVisibleOnRead() throws Exception {
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey/skip")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Skipped"));
        mockMvc.perform(get("/api/promises/" + promiseId + "/validation-survey")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Skipped"));
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey")
                        .header("Authorization", authorization).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualStarted\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reminderClaimIsOnceOnlyWithoutClosingTheSurvey() throws Exception {
        for (boolean expected : new boolean[] {true, false}) {
            mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey/inapp-reminder")
                            .header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.show").value(expected));
        }
        assertEquals("Pending", jdbc.queryForObject("select lifecycle_status from validation_survey where promise_id = ?", String.class, promiseId));
        mockMvc.perform(post("/api/promises/" + promiseId + "/validation-survey/inapp-reminder"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentReminderClaimsHaveExactlyOneWinner() throws Exception {
        var session = auth.authenticate(authorization);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> { gate.await(); return reminders.claim(promiseId, session); }));
            gate.countDown();
            int winners = 0;
            for (var future : futures) if (future.get()) winners++;
            assertEquals(1, winners);
        }
    }

    @Test
    void reminderRequiresEligibleOpenWindowAndLiveSession() {
        var session = auth.authenticate(authorization);
        Instant start = Instant.now(), end = start.plusSeconds(86400);
        jdbc.update("update participant_session set expires_at = ? where id = ?", Timestamp.from(end.plusSeconds(3600)), session.session().id());
        jdbc.update("update study_period set access_ends_at = ? where id = (select study_period_id from participant where id = ?)",
                Timestamp.from(end.plusSeconds(3600)), session.participant().id());
        jdbc.update("update validation_survey set available_at = ?, expires_at = ? where promise_id = ?",
                Timestamp.from(start), Timestamp.from(end), promiseId);
        var before = new InAppSurveyReminderService(jdbc, java.time.Clock.fixed(start.minusSeconds(1), java.time.ZoneOffset.UTC));
        var boundary = new InAppSurveyReminderService(jdbc, java.time.Clock.fixed(end, java.time.ZoneOffset.UTC));
        assertFalse(before.claim(promiseId, session));
        assertFalse(boundary.claim(promiseId, session));
        assertTrue(new InAppSurveyReminderService(jdbc, java.time.Clock.fixed(start, java.time.ZoneOffset.UTC)).claim(promiseId, session));
        jdbc.update("update validation_survey set inapp_reprompted_at = null where promise_id = ?", promiseId);
        // Use the seeded live session's own time for status/withdrawal checks.
        jdbc.update("update validation_survey set available_at = ?, expires_at = ? where promise_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(Instant.now().plusSeconds(3600)), promiseId);
        for (String state : new String[] {"Submitted", "Skipped", "Expired"}) {
            jdbc.update("update validation_survey set lifecycle_status = ? where promise_id = ?", state, promiseId);
            assertFalse(reminders.claim(promiseId, session));
        }
        jdbc.update("update validation_survey set lifecycle_status = 'Pending', eligibility_status = 'TechFailed' where promise_id = ?", promiseId);
        assertFalse(reminders.claim(promiseId, session));
        jdbc.update("update validation_survey set eligibility_status = 'AuthCompleted' where promise_id = ?", promiseId);
        jdbc.update("update participant set withdrawn_at = now() where id = ?", session.participant().id());
        assertFalse(reminders.claim(promiseId, session));
        jdbc.update("update participant set withdrawn_at = null where id = ?", session.participant().id());
        assertTrue(reminders.claim(promiseId, session));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
