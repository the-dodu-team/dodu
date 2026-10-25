package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhotoRecoveryBackoffPolicyTests {
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void usesBoundedExponentialDelayAndStopsAfterThreeAttempts() {
        PhotoRecoveryBackoffPolicy policy = new PhotoRecoveryBackoffPolicy();

        assertEquals(NOW.plusSeconds(30), policy.afterFailure(1, NOW).nextAttemptAt());
        assertEquals(NOW.plusSeconds(120), policy.afterFailure(2, NOW).nextAttemptAt());
        assertFalse(policy.afterFailure(3, NOW).retry());
    }

    @Test
    void failureBeforeMaximumSchedulesAnotherAttempt() {
        PhotoRecoveryBackoffPolicy.RetryPlan plan = new PhotoRecoveryBackoffPolicy()
                .afterFailure(0, NOW);

        assertTrue(plan.retry());
        assertEquals(0, plan.attemptCount());
    }
}
