package com.dodu.notification;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Server-time notification boundaries for one promise schedule generation. */
public final class NotificationSchedulePolicy {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<NotificationType> TYPES = List.of(
            NotificationType.START,
            NotificationType.REMINDER_10,
            NotificationType.REMINDER_20);

    public List<DueNotification> due(Instant scheduledAt, int scheduleVersion, Instant now) {
        return TYPES.stream()
                .map(type -> new DueNotification(type, scheduledAt.plus(type.offset()), scheduleVersion))
                .filter(candidate -> !candidate.scheduledAt().isAfter(now))
                .toList();
    }

    public Instant surveyReminderAt(Instant promiseScheduledAt) {
        ZonedDateTime kst = promiseScheduledAt.atZone(KST);
        if (kst.toLocalTime().isBefore(LocalTime.NOON)) {
            return kst.toLocalDate().atTime(18, 0).atZone(KST).toInstant();
        }
        return kst.toLocalDate().plusDays(1).atTime(11, 0).atZone(KST).toInstant();
    }

    public enum NotificationType {
        START(Duration.ZERO),
        REMINDER_10(Duration.ofMinutes(10)),
        REMINDER_20(Duration.ofMinutes(20)),
        SURVEY_REMINDER(Duration.ZERO);

        private final Duration offset;

        NotificationType(Duration offset) {
            this.offset = offset;
        }

        public Duration offset() {
            return offset;
        }
    }

    public record DueNotification(NotificationType type, Instant scheduledAt, int scheduleVersion) { }
}
