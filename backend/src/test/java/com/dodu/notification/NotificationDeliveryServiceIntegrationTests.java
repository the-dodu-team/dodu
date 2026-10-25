package com.dodu.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(NotificationDeliveryServiceIntegrationTests.ContainerConfiguration.class)
class NotificationDeliveryServiceIntegrationTests {
    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-25T09:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationDeliveryService service;

    @Test
    void doesNotCreateNewInterventionNotificationsAfterOfficialEnd() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(30 * 60));

        assertEquals(0, service.dispatchDueNotifications(SCHEDULED_AT.plusSeconds(60 * 60)));
        assertEquals(0, deliveries(fixture.promiseId()));
    }

    @Test
    void createsDueInAppNotificationsBeforeOfficialEnd() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(2 * 60 * 60));

        assertEquals(3, service.dispatchDueNotifications(SCHEDULED_AT.plusSeconds(20 * 60)));
        assertEquals(3, deliveries(fixture.promiseId()));
    }

    private Fixture fixture(Instant interventionEndsAt) {
        String suffix = UUID.randomUUID().toString();
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID promiseId = UUID.randomUUID();
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "notification-study-" + suffix, Timestamp.from(interventionEndsAt));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "notification-policy-" + suffix, Timestamp.from(SCHEDULED_AT.minusSeconds(600)));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "notification-" + suffix, Timestamp.from(SCHEDULED_AT.minusSeconds(600)));
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, 'notification task', ?, ?, 'laptop', 'NORMAL', 'Scheduled', ?)",
                promiseId, participantId, policyId, Timestamp.from(SCHEDULED_AT),
                SCHEDULED_AT.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                Timestamp.from(SCHEDULED_AT.plusSeconds(3 * 60 * 60)));
        return new Fixture(promiseId);
    }

    private int deliveries(UUID promiseId) {
        return jdbc.queryForObject("select count(*) from notification_delivery where promise_id = ? and channel = 'IN_APP'",
                Integer.class, promiseId);
    }

    private record Fixture(UUID promiseId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
