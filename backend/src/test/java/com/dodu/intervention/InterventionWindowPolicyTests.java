package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterventionWindowPolicyTests {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private InterventionWindowPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new InterventionWindowPolicy(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsExactFiveMinuteAndSeventyTwoHourScheduleBoundaries() {
        Instant officialEnd = NOW.plusSeconds(72 * 60 * 60 + 30 * 60);

        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.ALLOWED,
                policy.validatePromiseSchedule(NOW.plus(Duration.ofMinutes(5)), officialEnd));
        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.ALLOWED,
                policy.validatePromiseSchedule(NOW.plus(Duration.ofHours(72)), officialEnd));
    }

    @Test
    void rejectsSchedulesOutsideServerTimeRange() {
        Instant officialEnd = NOW.plus(Duration.ofDays(10));

        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.TOO_EARLY,
                policy.validatePromiseSchedule(NOW.plus(Duration.ofMinutes(4)).plusSeconds(59), officialEnd));
        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.TOO_LATE,
                policy.validatePromiseSchedule(NOW.plus(Duration.ofHours(72)).plusSeconds(1), officialEnd));
    }

    @Test
    void rejectsScheduleWhoseFullWindowPassesOfficialEnd() {
        Instant scheduledAt = NOW.plus(Duration.ofHours(1));

        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.PAST_INTERVENTION_END,
                policy.validatePromiseSchedule(scheduledAt, scheduledAt.plus(Duration.ofMinutes(29)).plusSeconds(59)));
        assertEquals(
                InterventionWindowPolicy.ScheduleDecision.ALLOWED,
                policy.validatePromiseSchedule(scheduledAt, scheduledAt.plus(Duration.ofMinutes(30))));
    }

    @Test
    void closesNewInterventionCreationAtOfficialEnd() {
        assertTrue(policy.isNewInterventionOpen(NOW.plusSeconds(1)));
        assertFalse(policy.isNewInterventionOpen(NOW));
        assertFalse(policy.isNewInterventionOpen(NOW.minusSeconds(1)));
    }

}
