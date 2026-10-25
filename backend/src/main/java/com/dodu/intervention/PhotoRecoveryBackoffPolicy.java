package com.dodu.intervention;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Bounded exponential backoff for provider failures; attempt count is server-owned. */
public final class PhotoRecoveryBackoffPolicy {
    public static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofMinutes(10);

    public RetryPlan afterFailure(int completedAttempts, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (completedAttempts >= MAX_ATTEMPTS) {
            return new RetryPlan(false, completedAttempts, null);
        }
        Duration delay = switch (completedAttempts) {
            case 0, 1 -> INITIAL_DELAY;
            case 2 -> Duration.ofMinutes(2);
            default -> MAX_DELAY;
        };
        if (delay.compareTo(MAX_DELAY) > 0) delay = MAX_DELAY;
        return new RetryPlan(true, completedAttempts, now.plus(delay));
    }

    public record RetryPlan(boolean retry, int attemptCount, Instant nextAttemptAt) { }
}
