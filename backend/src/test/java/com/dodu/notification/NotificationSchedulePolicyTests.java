package com.dodu.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationSchedulePolicyTests {
    private final NotificationSchedulePolicy policy = new NotificationSchedulePolicy();
    private final Instant scheduledAt = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void emitsStartAndBothRemindersWhenSchedulerWasTemporarilyDown() {
        List<NotificationSchedulePolicy.DueNotification> due = policy.due(
                scheduledAt, 1, scheduledAt.plusSeconds(20 * 60));

        assertEquals(List.of(
                NotificationSchedulePolicy.NotificationType.START,
                NotificationSchedulePolicy.NotificationType.REMINDER_10,
                NotificationSchedulePolicy.NotificationType.REMINDER_20),
                due.stream().map(NotificationSchedulePolicy.DueNotification::type).toList());
        assertTrue(due.stream().allMatch(item -> item.scheduleVersion() == 1));
    }

    @Test
    void keepsFutureReminderOutOfDueSet() {
        List<NotificationSchedulePolicy.DueNotification> due = policy.due(
                scheduledAt, 2, scheduledAt.plusSeconds(10 * 60));

        assertEquals(2, due.size());
        assertTrue(due.stream().noneMatch(item ->
                item.type() == NotificationSchedulePolicy.NotificationType.REMINDER_20));
        assertTrue(due.stream().allMatch(item -> item.scheduleVersion() == 2));
    }

    @Test
    void schedulesSurveyReminderAtSixPmForMorningPromise() {
        Instant morning = Instant.parse("2026-10-25T02:00:00Z");

        assertEquals(Instant.parse("2026-10-25T09:00:00Z"), policy.surveyReminderAt(morning));
    }

    @Test
    void schedulesSurveyReminderAtElevenNextDayForAfternoonPromise() {
        Instant afternoon = Instant.parse("2026-10-25T03:00:00Z");

        assertEquals(Instant.parse("2026-10-26T02:00:00Z"), policy.surveyReminderAt(afternoon));
    }
}
