package com.dodu.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates idempotent delivery records from the server clock. */
@Service
public class NotificationDeliveryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PushNotificationSender pushSender;
    private final NotificationSchedulePolicy policy = new NotificationSchedulePolicy();

    public NotificationDeliveryService(JdbcTemplate jdbc, Clock clock, PushNotificationSender pushSender) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.pushSender = pushSender;
    }

    @Scheduled(fixedDelayString = "${dodu.notifications.scheduler-delay-ms:30000}")
    @Transactional
    public void dispatchDueNotifications() {
        dispatchDueNotifications(clock.instant());
    }

    @Transactional
    public int dispatchDueNotifications(Instant now) {
        List<PromiseRow> promises = jdbc.query("""
                select p.id, p.participant_id, p.scheduled_at, p.schedule_version
                  from promise p
                  join participant part on part.id = p.participant_id
                  join study_period study on study.id = part.study_period_id
                 where p.lifecycle_status in ('Scheduled', 'AuthAvailable')
                   and part.lifecycle_status <> 'Withdrawn'
                   and p.scheduled_at <= ?
                   and p.auth_window_ends_at > ?
                   and (study.intervention_ends_at is null or p.scheduled_at < study.intervention_ends_at)
                """, (rs, rowNum) -> new PromiseRow(rs.getObject("id", UUID.class),
                        rs.getObject("participant_id", UUID.class),
                        rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getInt("schedule_version")), timestamp(now), timestamp(now));
        int created = 0;
        for (PromiseRow promise : promises) {
            boolean evaluating = jdbc.queryForObject(
                    "select exists(select 1 from auth_attempt where promise_id = ? and processing_status = 'Evaluating')",
                    Boolean.class, promise.promiseId());
            for (NotificationSchedulePolicy.DueNotification due : policy.due(
                    promise.scheduledAt(), promise.scheduleVersion(), now)) {
                if (due.type() != NotificationSchedulePolicy.NotificationType.START && evaluating) {
                    continue;
                }
                String dispatchKey = promise.promiseId() + ":" + due.scheduleVersion() + ":" + due.type().name();
                created += insertInApp(promise, due, dispatchKey, now);
                created += insertPush(promise, due, dispatchKey, now);
            }
        }
        return created + dispatchSurveyReminders(now);
    }

    private int dispatchSurveyReminders(Instant now) {
        List<SurveyRow> surveys = jdbc.query("""
                select vs.promise_id, p.participant_id, p.scheduled_at, p.schedule_version,
                       vs.expires_at
                  from validation_survey vs
                  join promise p on p.id = vs.promise_id
                  join participant part on part.id = p.participant_id
                 where p.lifecycle_status in ('AuthCompleted', 'AuthIncomplete')
                   and part.lifecycle_status <> 'Withdrawn'
                   and vs.lifecycle_status in ('Pending', 'Prompted')
                   and vs.available_at is not null and vs.expires_at is not null
                   and vs.reminder_sent_at is null
                """, (rs, rowNum) -> new SurveyRow(rs.getObject("promise_id", UUID.class),
                        rs.getObject("participant_id", UUID.class),
                        rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getInt("schedule_version"), rs.getTimestamp("expires_at").toInstant()));
        int created = 0;
        for (SurveyRow survey : surveys) {
            Instant reminderAt = policy.surveyReminderAt(survey.scheduledAt());
            if (now.isBefore(reminderAt) || !now.isBefore(survey.expiresAt())) continue;
            int reminded = jdbc.update("""
                    update validation_survey
                       set lifecycle_status = 'Reminded', reminder_sent_at = ?
                     where promise_id = ? and lifecycle_status in ('Pending', 'Prompted')
                       and reminder_sent_at is null
                    """, timestamp(now), survey.promiseId());
            if (reminded == 0) continue;
            NotificationSchedulePolicy.DueNotification due = new NotificationSchedulePolicy.DueNotification(
                    NotificationSchedulePolicy.NotificationType.SURVEY_REMINDER, reminderAt,
                    survey.scheduleVersion());
            String dispatchKey = survey.promiseId() + ":" + due.scheduleVersion() + ":SURVEY_REMINDER";
            PromiseRow promise = new PromiseRow(survey.promiseId(), survey.participantId(),
                    survey.scheduledAt(), survey.scheduleVersion());
            created += insertInApp(promise, due, dispatchKey, now);
            created += insertPush(promise, due, dispatchKey, now);
        }
        return created;
    }

    private int insertInApp(PromiseRow promise, NotificationSchedulePolicy.DueNotification due,
                            String dispatchKey, Instant now) {
        return jdbc.update("""
                insert into notification_delivery
                    (participant_id, promise_id, schedule_version, notification_type, channel,
                     dispatch_key, lifecycle_status, scheduled_at, sent_at)
                values (?, ?, ?, ?, 'IN_APP', ?, 'SENT', ?, ?)
                on conflict (dispatch_key, channel) do nothing
                """, promise.participantId(), promise.promiseId(), due.scheduleVersion(), due.type().name(),
                dispatchKey, timestamp(due.scheduledAt()), timestamp(now));
    }

    private int insertPush(PromiseRow promise, NotificationSchedulePolicy.DueNotification due,
                           String dispatchKey, Instant now) {
        int inserted = jdbc.update("""
                insert into notification_delivery
                    (participant_id, promise_id, schedule_version, notification_type, channel,
                     dispatch_key, lifecycle_status, scheduled_at)
                select ?, ?, ?, ?, 'PUSH', ?, 'SCHEDULED', ?
                 where exists (select 1 from notification_subscription
                                where participant_id = ? and lifecycle_status = 'Active')
                on conflict (dispatch_key, channel) do nothing
                """, promise.participantId(), promise.promiseId(), due.scheduleVersion(), due.type().name(),
                dispatchKey, timestamp(due.scheduledAt()), promise.participantId());
        if (inserted == 0) return 0;
        PushNotificationSender.PushSendResult result = pushSender.send(
                promise.participantId(), promise.promiseId(), due.type());
        String status = switch (result.status()) {
            case SENT -> "SENT";
            case FAILED -> "FAILED";
            case UNCONFIGURED -> "UNCONFIGURED";
        };
        jdbc.update("""
                update notification_delivery
                   set lifecycle_status = ?, provider_message_id = ?, sent_at = ?, failure_code = ?
                 where dispatch_key = ? and channel = 'PUSH'
                """, status, result.providerMessageId(),
                result.status() == PushNotificationSender.Status.SENT ? timestamp(now) : null,
                result.failureCode(), dispatchKey);
        return 1;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record PromiseRow(UUID promiseId, UUID participantId, Instant scheduledAt, int scheduleVersion) { }
    private record SurveyRow(UUID promiseId, UUID participantId, Instant scheduledAt,
                             int scheduleVersion, Instant expiresAt) { }
}
