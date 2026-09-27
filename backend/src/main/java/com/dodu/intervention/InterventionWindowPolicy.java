package com.dodu.intervention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Server-side time rules shared by promise, notification, and authentication use cases.
 *
 * <p>The policy deliberately returns decisions instead of throwing HTTP exceptions. Callers
 * can map the same domain decision to their own API contract without moving time rules into
 * controllers.</p>
 */
public final class InterventionWindowPolicy {

    public static final Duration MIN_SCHEDULE_OFFSET = Duration.ofMinutes(5);
    public static final Duration MAX_SCHEDULE_OFFSET = Duration.ofHours(72);
    public static final Duration AUTHENTICATION_WINDOW = Duration.ofMinutes(30);

    private final Clock clock;

    public InterventionWindowPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Validates a new or changed promise against server time and the official intervention end.
     * The official end is inclusive for the end of the full T~T+30 intervention window.
     */
    public ScheduleDecision validatePromiseSchedule(Instant scheduledAt, Instant officialEndAt) {
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(officialEndAt, "officialEndAt must not be null");

        Instant now = clock.instant();
        if (scheduledAt.isBefore(now.plus(MIN_SCHEDULE_OFFSET))) {
            return ScheduleDecision.TOO_EARLY;
        }
        if (scheduledAt.isAfter(now.plus(MAX_SCHEDULE_OFFSET))) {
            return ScheduleDecision.TOO_LATE;
        }
        if (scheduledAt.plus(AUTHENTICATION_WINDOW).isAfter(officialEndAt)) {
            return ScheduleDecision.PAST_INTERVENTION_END;
        }
        return ScheduleDecision.ALLOWED;
    }

    /**
     * Returns whether a new intervention may be created at the server's current instant.
     * The official end instant is closed for new intervention creation.
     */
    public boolean isNewInterventionOpen(Instant officialEndAt) {
        Objects.requireNonNull(officialEndAt, "officialEndAt must not be null");
        return clock.instant().isBefore(officialEndAt);
    }

    public enum ScheduleDecision {
        ALLOWED,
        TOO_EARLY,
        TOO_LATE,
        PAST_INTERVENTION_END
    }
}
