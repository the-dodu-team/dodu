package com.dodu.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionToken;
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

/** 개발자 QA용으로 하루 1약속 제한을 끈 경우(dodu.promise.daily-limit.enabled=false). */
@SpringBootTest(properties = "dodu.promise.daily-limit.enabled=false")
@Import(PromiseDailyLimitDisabledIntegrationTests.ContainerConfiguration.class)
class PromiseDailyLimitDisabledIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private SecretHasher secretHasher;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String authorization;
    private String body;
    private UUID participantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = SessionToken.generate(new SecureRandom());
        UUID studyId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "no-limit-" + suffix, Timestamp.from(now.plusSeconds(172_800)));
        jdbc.update("insert into policy_version(version, effective_from) values (?, ?)",
                "no-limit-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "no-limit-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                deviceId, participantId, "device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                UUID.randomUUID(), participantId, deviceId, secretHasher.hash(token), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        authorization = "Bearer " + token;
        String scheduledAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).withNano(0).toString();
        body = "{\"taskTitle\":\"qa task\",\"toolType\":\"BOOK\",\"scheduledAt\":\"" + scheduledAt + "\"}";
    }

    @Test
    void finishedPromiseDoesNotBlockSameDateAndStaysInHistory() throws Exception {
        UUID completed = UUID.fromString(create("qa-1"));
        jdbc.update("update promise set lifecycle_status = 'AuthCompleted', final_outcome = 'AuthCompleted' where id = ?", completed);

        UUID second = UUID.fromString(create("qa-2"));

        assertEquals(2, jdbc.queryForObject("select count(*) from promise where participant_id = ?", Integer.class, participantId));
        assertEquals(null, jdbc.queryForObject("select superseded_by_promise_id from promise where id = ?", UUID.class, completed));
        assertEquals("NORMAL", jdbc.queryForObject("select creation_type from promise where id = ?", String.class, second));
    }

    @Test
    void failedPromiseIsStillReplacedWhenLimitIsOff() throws Exception {
        UUID failed = UUID.fromString(create("qa-fail-1"));
        jdbc.update("update promise set lifecycle_status = 'AuthIncomplete', final_outcome = 'AuthIncomplete' where id = ?", failed);

        UUID recreated = UUID.fromString(create("qa-fail-2"));

        assertEquals(recreated, jdbc.queryForObject("select superseded_by_promise_id from promise where id = ?", UUID.class, failed));
        assertEquals("RECREATED", jdbc.queryForObject("select creation_type from promise where id = ?", String.class, recreated));
    }

    @Test
    void meTellsScreenThatDailyLimitIsOff() throws Exception {
        mockMvc.perform(get("/api/participant/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyPromiseLimit").value(false));
    }

    private String create(String key) throws Exception {
        String response = mockMvc.perform(post("/api/promises").header("Authorization", authorization)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceFirst(".*\\\"promiseId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
