package com.dodu.notification;

import com.dodu.intervention.InterventionLifecycleGate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates idempotent delivery records from the server clock. */
@Service
public class NotificationDeliveryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PushNotificationSender pushSender;
    private final NotificationSchedulePolicy policy = new NotificationSchedulePolicy();
    private final PushRetryPolicy pushRetryPolicy = new PushRetryPolicy();
    private final InterventionLifecycleGate lifecycleGate = new InterventionLifecycleGate();
    private final TransactionTemplate tx;

    public NotificationDeliveryService(JdbcTemplate jdbc, Clock clock,
                                       PushNotificationSender pushSender,
                                       PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.pushSender = pushSender;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${dodu.notifications.scheduler-delay-ms:30000}")
    public void dispatchDueNotifications() {
        dispatchDueNotifications(clock.instant());
    }

    public int dispatchDueNotifications(Instant now) {
        // 1단계: 짧은 트랜잭션. 알림 행만 만든다 (외부 호출 없음)
        Integer created = tx.execute(status -> createDueRows(now));
        // 2단계: 트랜잭션 밖. 푸시를 발송하고 행마다 결과를 바로 저장한다
        int retried = sendPendingPushes(now);
        return created + retried;
    }

    private int createDueRows(Instant now) {
        List<PromiseRow> promises = jdbc.query("""
                select p.id, p.participant_id, p.scheduled_at, p.schedule_version
                       , study.intervention_ends_at
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
                        rs.getInt("schedule_version"),
                        rs.getTimestamp("intervention_ends_at") == null
                                ? null : rs.getTimestamp("intervention_ends_at").toInstant()),
                timestamp(now), timestamp(now));
        int created = 0;
        for (PromiseRow promise : promises) {
            if (!isNewInterventionAllowed(now, promise.interventionEndsAt())) continue;
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

    private int sendPendingPushes(Instant now) {
        List<PushRow> pending = jdbc.query("""
            select id, participant_id, promise_id, notification_type, schedule_version, attempt_count
              from notification_delivery
             where channel = 'PUSH'
               and (lifecycle_status = 'SCHEDULED'
                    or (lifecycle_status = 'FAILED'
                        and next_attempt_at is not null and next_attempt_at <= ?))
             order by coalesce(next_attempt_at, scheduled_at), created_at
             limit 10
            """, (rs, rowNum) -> new PushRow(rs.getObject("id", UUID.class),
                rs.getObject("participant_id", UUID.class), rs.getObject("promise_id", UUID.class),
                NotificationSchedulePolicy.NotificationType.valueOf(rs.getString("notification_type")),
                rs.getInt("schedule_version"), rs.getInt("attempt_count")), timestamp(now));

        int retried = 0;

        for (PushRow push : pending) {
            String reason = suppressionReason(push, now);
            if (reason != null) {
                jdbc.update("""
                        update notification_delivery
                           set lifecycle_status = 'SUPPRESSED', failure_code = ?, next_attempt_at = null
                         where id = ? and lifecycle_status in ('SCHEDULED', 'FAILED')
                        """, reason, push.id());
                continue;
            }
            // 트랜잭션 밖에서 외부 호출
            PushNotificationSender.PushSendResult result = pushSender.send(
                    push.participantId(), push.promiseId(), push.type());
            int attemptCount = push.attemptCount() + 1;
            Instant nextAttemptAt = result.status() == PushNotificationSender.Status.FAILED
                    ? pushRetryPolicy.nextAttemptAt(now, attemptCount) : null;

            String status = switch (result.status()) {
                case SENT -> "SENT";
                case FAILED, UNDELIVERABLE -> "FAILED";
                case UNCONFIGURED -> "UNCONFIGURED";
            };

            jdbc.update("""
                update notification_delivery
                   set lifecycle_status = ?, provider_message_id = ?, sent_at = ?, failure_code = ?,
                       attempt_count = ?, next_attempt_at = ?
                 where id = ? and lifecycle_status in ('SCHEDULED', 'FAILED')
                """, status, result.providerMessageId(),
                    result.status() == PushNotificationSender.Status.SENT ? timestamp(now) : null,
                    result.failureCode(), attemptCount,
                    nextAttemptAt == null ? null : timestamp(nextAttemptAt), push.id());
            if (push.attemptCount() > 0) retried++;
        }
        return retried;
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
                    survey.scheduledAt(), survey.scheduleVersion(), null);
            created += insertInApp(promise, due, dispatchKey, now);
            created += insertPush(promise, due, dispatchKey, now);
        }
        return created;
    }

    private boolean isNewInterventionAllowed(Instant now, Instant interventionEndsAt) {
        if (interventionEndsAt == null) return true;
        return lifecycleGate.decide(now, interventionEndsAt, false)
                == InterventionLifecycleGate.Decision.NEW_WORK_ALLOWED;
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
        return jdbc.update("""
            insert into notification_delivery
                (participant_id, promise_id, schedule_version, notification_type, channel,
                 dispatch_key, lifecycle_status, scheduled_at)
            select ?, ?, ?, ?, 'PUSH', ?, 'SCHEDULED', ?
             where exists (select 1 from notification_subscription
                            where participant_id = ? and lifecycle_status = 'Active')
            on conflict (dispatch_key, channel) do nothing
            """, promise.participantId(), promise.promiseId(), due.scheduleVersion(), due.type().name(),
                dispatchKey, timestamp(due.scheduledAt()), promise.participantId());
    }

    /** 보내면 안 되는 이유를 반환하고, 보내도 되면 null. */
    private String suppressionReason(PushRow push, Instant now) {
        DeliveryState s = jdbc.queryForObject("""
                select part.lifecycle_status as participant_status,
                       p.lifecycle_status as promise_status, p.schedule_version,
                       p.auth_window_ends_at,
                       vs.lifecycle_status as survey_status, vs.expires_at as survey_expires_at
                  from promise p
                  join participant part on part.id = p.participant_id
                  left join validation_survey vs on vs.promise_id = p.id
                 where p.id = ?
                """, (rs, rowNum) -> new DeliveryState(
                        rs.getString("participant_status"), rs.getString("promise_status"),
                        rs.getInt("schedule_version"),
                        rs.getTimestamp("auth_window_ends_at") == null
                                ? null : rs.getTimestamp("auth_window_ends_at").toInstant(),
                        rs.getString("survey_status"),
                        rs.getTimestamp("survey_expires_at") == null
                                ? null : rs.getTimestamp("survey_expires_at").toInstant()),
                push.promiseId());
        if ("Withdrawn".equals(s.participantStatus())) return "PARTICIPANT_WITHDRAWN";

        if (push.type() == NotificationSchedulePolicy.NotificationType.SURVEY_REMINDER) {
            if (s.surveyExpiresAt() == null || !now.isBefore(s.surveyExpiresAt())) return "SURVEY_EXPIRED";
            if (!"Reminded".equals(s.surveyStatus())) return "SURVEY_NOT_PENDING";
            return null;
        }
        if (s.scheduleVersion() != push.scheduleVersion()) return "SCHEDULE_CHANGED";
        if (!List.of("Scheduled", "AuthAvailable").contains(s.promiseStatus())) return "PROMISE_NOT_ACTIVE";
        if (s.authWindowEndsAt() == null || !now.isBefore(s.authWindowEndsAt())) return "AUTH_WINDOW_ENDED";
        if (push.type() != NotificationSchedulePolicy.NotificationType.START
                && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from auth_attempt where promise_id = ? and processing_status = 'Evaluating')",
                Boolean.class, push.promiseId()))) {
            return "EVALUATING";
        }
        return null;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record PromiseRow(UUID promiseId, UUID participantId, Instant scheduledAt, int scheduleVersion,
                              Instant interventionEndsAt) { }
    private record SurveyRow(UUID promiseId, UUID participantId, Instant scheduledAt,
                             int scheduleVersion, Instant expiresAt) { }
    private record PushRow(UUID id, UUID participantId, UUID promiseId,
                           NotificationSchedulePolicy.NotificationType type,
                           int scheduleVersion, int attemptCount) { }

    private record DeliveryState(String participantStatus, String promiseStatus, int scheduleVersion,
                                 Instant authWindowEndsAt, String surveyStatus, Instant surveyExpiresAt) { }
}
