package com.dodu.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
@Import(NotificationControllerIntegrationTests.ContainerConfiguration.class)
class NotificationControllerIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String authorization;
    private UUID promiseId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString();
        String token = "N".repeat(11) + suffix.replace("-", "");
        String tokenHash = new SecretHasher("test-only-pepper-0123456789abcdef0123456789abcdef".getBytes()).hash(token);
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        promiseId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update("insert into study_period(id, code, access_ends_at, intervention_ends_at) values (?, ?, ?, ?)",
                studyId, "nc-study-" + suffix, Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(86_400)));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "nc-policy-" + suffix, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "notification-controller-" + suffix, Timestamp.from(now));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                deviceId, participantId, "notification-controller-device-" + suffix, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                sessionId, participantId, deviceId, tokenHash, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3_600)));
        Instant scheduledAt = now.minusSeconds(600);
        jdbc.update("""
                insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst,
                                    tool_type, creation_type, lifecycle_status, auth_window_ends_at)
                values (?, ?, ?, 'notification controller task', ?, ?, 'laptop', 'NORMAL', 'Scheduled', ?)
                """, promiseId, participantId, policyId, Timestamp.from(scheduledAt),
                scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), Timestamp.from(now.plusSeconds(1_800)));
        authorization = "Bearer " + token;

        jdbc.update("""
                insert into notification_delivery(id, participant_id, promise_id, schedule_version, notification_type,
                                                  channel, dispatch_key, lifecycle_status, scheduled_at, sent_at)
                values (?, ?, ?, 0, 'START', 'IN_APP', ?, 'SENT', ?, ?),
                       (?, ?, ?, 0, 'T_PLUS_10', 'IN_APP', ?, 'FAILED', ?, null),
                       (?, ?, ?, 0, 'T_PLUS_20', 'IN_APP', ?, 'SCHEDULED', ?, null)
                """, notificationId, participantId, promiseId, "sent-" + suffix, Timestamp.from(scheduledAt), Timestamp.from(now),
                UUID.randomUUID(), participantId, promiseId, "failed-" + suffix, Timestamp.from(now),
                UUID.randomUUID(), participantId, promiseId, "scheduled-" + suffix, Timestamp.from(now));
    }

    @Test
    void listsOnlySentInAppNotificationsForTheCurrentParticipant() throws Exception {
        mockMvc.perform(get("/api/participant/notifications").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].notificationType").value("START"))
                .andExpect(jsonPath("$.data[0].sentAt").exists())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void marksOnlyOwnedSentInAppNotificationAsReadAndIsIdempotent() throws Exception {
        mockMvc.perform(put("/api/participant/notifications/" + notificationId + "/read")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.data.readAt").exists());

        String firstReadAt = jdbc.queryForObject("select read_at::text from notification_delivery where id = ?", String.class, notificationId);
        mockMvc.perform(put("/api/participant/notifications/" + notificationId + "/read")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(firstReadAt,
                jdbc.queryForObject("select read_at::text from notification_delivery where id = ?", String.class, notificationId));
    }

    @Test
    void doesNotExposeFailedOrScheduledNotificationAsReadable() throws Exception {
        UUID failedId = jdbc.queryForObject("select id from notification_delivery where lifecycle_status = 'FAILED'", UUID.class);
        mockMvc.perform(put("/api/participant/notifications/" + failedId + "/read")
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
