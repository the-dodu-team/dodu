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

    @Test
    void rerunningSchedulerDoesNotDuplicateNotificationDeliveries() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(2 * 60 * 60));
        Instant dispatchAt = SCHEDULED_AT.plusSeconds(20 * 60);

        assertEquals(3, service.dispatchDueNotifications(dispatchAt));
        assertEquals(0, service.dispatchDueNotifications(dispatchAt.plusSeconds(30)));
        assertEquals(3, deliveries(fixture.promiseId()));
    }

    @Test
    void withdrawnParticipantDoesNotReceiveInterventionNotifications() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(2 * 60 * 60));
        jdbc.update("update participant set lifecycle_status = 'Withdrawn' where id = ?", fixture.participantId());

        assertEquals(0, service.dispatchDueNotifications(SCHEDULED_AT.plusSeconds(20 * 60)));
        assertEquals(0, deliveries(fixture.promiseId()));
    }

    @Test
    void cancelledPromiseDoesNotReceiveInterventionNotifications() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(2 * 60 * 60));
        jdbc.update("update promise set lifecycle_status = 'Closed', final_outcome = 'Cancelled' where id = ?",
                fixture.promiseId());

        assertEquals(0, service.dispatchDueNotifications(SCHEDULED_AT.plusSeconds(20 * 60)));
        assertEquals(0, deliveries(fixture.promiseId()));
    }

    @Test
    void surveyReminderRemainsAvailableAfterOfficialInterventionEnd() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(30 * 60));
        Instant reminderAt = SCHEDULED_AT.plusSeconds(17 * 60 * 60);
        jdbc.update("update promise set lifecycle_status = 'AuthCompleted' where id = ?", fixture.promiseId());
        jdbc.update("""
                insert into validation_survey(promise_id, questionnaire_version, lifecycle_status,
                                              eligibility_status, available_at, expires_at)
                values (?, 'v2.5.2', 'Pending', 'Eligible', ?, ?)
                """, fixture.promiseId(), Timestamp.from(SCHEDULED_AT.minusSeconds(60)),
                Timestamp.from(reminderAt.plusSeconds(24 * 60 * 60)));

        assertEquals(1, service.dispatchDueNotifications(reminderAt));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from notification_delivery where promise_id = ? and notification_type = 'SURVEY_REMINDER' and channel = 'IN_APP'",
                Integer.class, fixture.promiseId()));
        assertEquals("Reminded", jdbc.queryForObject(
                "select lifecycle_status from validation_survey where promise_id = ?", String.class, fixture.promiseId()));
    }

    @Test
    void expiredSurveyDoesNotReceiveReminder() {
        Fixture fixture = fixture(SCHEDULED_AT.plusSeconds(2 * 60 * 60));
        Instant expiredAt = SCHEDULED_AT.plusSeconds(60 * 60);
        jdbc.update("update promise set lifecycle_status = 'AuthCompleted' where id = ?", fixture.promiseId());
        jdbc.update("""
                insert into validation_survey(promise_id, questionnaire_version, lifecycle_status,
                                              eligibility_status, available_at, expires_at)
                values (?, 'v2.5.2', 'Pending', 'Eligible', ?, ?)
                """, fixture.promiseId(), Timestamp.from(SCHEDULED_AT.minusSeconds(60)), Timestamp.from(expiredAt));

        assertEquals(0, service.dispatchDueNotifications(expiredAt));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from notification_delivery where promise_id = ? and notification_type = 'SURVEY_REMINDER'",
                Integer.class, fixture.promiseId()));
        assertEquals("Pending", jdbc.queryForObject(
                "select lifecycle_status from validation_survey where promise_id = ?", String.class, fixture.promiseId()));
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
        return new Fixture(promiseId, participantId);
    }

    private int deliveries(UUID promiseId) {
        return jdbc.queryForObject("select count(*) from notification_delivery where promise_id = ? and channel = 'IN_APP'",
                Integer.class, promiseId);
    }

    private record Fixture(UUID promiseId, UUID participantId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
