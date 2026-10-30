package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(PhotoSubmissionControllerIntegrationTests.ContainerConfiguration.class)
class PhotoSubmissionControllerIntegrationTests {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SecretHasher secretHasher;

    private MockMvc mockMvc;
    private String authorization;
    private UUID promiseId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = SessionToken.generate(new SecureRandom());
        String tokenHash = secretHasher.hash(token);
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        promiseId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)", studyId, "photo-study-" + suffix, Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)", policyId, "photo-policy-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)", participantId, studyId, "photo-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)", deviceId, participantId, "photo-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)", sessionId, participantId, deviceId, tokenHash, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status) values (?, ?, ?, 'photo task', ?, ?, 'laptop', 'NORMAL', 'Scheduled')", promiseId, participantId, policyId, Timestamp.from(now.minusSeconds(300)), now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
        authorization = "Bearer " + token;
    }

    @Test
    void submissionStoresMetadataAndRepeatingSameRequestReturnsSameAttempt() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {1, 2, 3});
        var first = mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(photo).param("clientRequestId", requestId.toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").exists()).andReturn();
        String attemptId = first.getResponse().getContentAsString().replaceFirst(".*\\\"attemptId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("clientRequestId", requestId.toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(attemptId));
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_attempt where promise_id = ?", Integer.class, promiseId));
        assertEquals(1, jdbc.queryForObject("select count(*) from photo_asset", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from photo_evaluation_job where promise_id = ?", Integer.class, promiseId));
    }

    @Test
    void repeatingRequestIdWithDifferentPhotoConflicts() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("clientRequestId", requestId.toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "other.jpg", "image/jpeg", new byte[] {9, 8, 7}))
                        .param("clientRequestId", requestId.toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isConflict());
    }

    @Test
    void submissionBeforeScheduledStartIsRejected() throws Exception {
        Instant scheduledAt = Instant.now().plusSeconds(60);
        jdbc.update("update promise set scheduled_at = ?, scheduled_date_kst = ? where id = ?",
                Timestamp.from(scheduledAt), scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), promiseId);

        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHOTO_WINDOW_CLOSED"));
    }

    @Test
    void submissionAtOrAfterTPlusThirtyIsRejected() throws Exception {
        Instant scheduledAt = Instant.now().minusSeconds(31 * 60);
        jdbc.update("update promise set scheduled_at = ?, scheduled_date_kst = ? where id = ?",
                Timestamp.from(scheduledAt), scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), promiseId);

        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHOTO_WINDOW_CLOSED"));
    }

    @Test
    void samePhotoIsAllowedForSamePromiseButBlockedOnAnotherScheduledDay() throws Exception {
        UUID firstRequestId = UUID.randomUUID();
        byte[] photoBytes = new byte[] {1, 2, 3};
        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", photoBytes))
                        .param("clientRequestId", firstRequestId.toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        jdbc.update("update auth_attempt set processing_status = 'TechFailed' where promise_id = ?", promiseId);

        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", photoBytes))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());

        UUID priorDayPromiseId = UUID.randomUUID();
        UUID participantId = jdbc.queryForObject("select participant_id from promise where id = ?", UUID.class, promiseId);
        UUID policyId = jdbc.queryForObject("select policy_version_id from promise where id = ?", UUID.class, promiseId);
        Instant scheduledAt = Instant.now().minusSeconds(24 * 60 * 60 + 300);
        LocalDate priorDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        jdbc.update("""
                insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst,
                                    tool_type, creation_type, lifecycle_status)
                values (?, ?, ?, 'prior-day photo task', ?, ?, 'laptop', 'NORMAL', 'Scheduled')
                """, priorDayPromiseId, participantId, policyId, Timestamp.from(scheduledAt), priorDate);
        String photoHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(photoBytes));
        UUID assetId = UUID.randomUUID();
        jdbc.update("""
                insert into photo_asset(id, storage_key, content_type, byte_size, sha256_hash, received_at)
                values (?, ?, 'image/jpeg', ?, ?, ?)
                """, assetId, UUID.randomUUID(), photoBytes.length, photoHash, Timestamp.from(scheduledAt));
        jdbc.update("""
                insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type,
                                         processing_status, request_received_at, valid_received_at, photo_asset_id)
                values (?, ?, ?, 'prior-day-fingerprint', 'laptop', 'Evaluating', ?, ?, ?)
                """, UUID.randomUUID(), priorDayPromiseId, UUID.randomUUID(), Timestamp.from(scheduledAt),
                Timestamp.from(scheduledAt), assetId);

        mockMvc.perform(multipart("/api/promises/" + promiseId + "/photo-attempts")
                        .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", photoBytes))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                        .header("Authorization", authorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHOTO_ALREADY_USED_ON_ANOTHER_DAY"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
