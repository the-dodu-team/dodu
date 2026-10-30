package com.dodu.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushRetryPolicyTests {
    private final PushRetryPolicy policy = new PushRetryPolicy();
    private final Instant now = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void usesExponentialBackoffForThreeRetries() {
        assertEquals(now.plusSeconds(30), policy.nextAttemptAt(now, 0));
        assertEquals(now.plusSeconds(120), policy.nextAttemptAt(now, 1));
        assertEquals(now.plusSeconds(600), policy.nextAttemptAt(now, 2));
    }

    @Test
    void stopsAfterThreeAttempts() {
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(2));
        assertFalse(policy.canRetry(3));
        assertNull(policy.nextAttemptAt(now, 3));
    }
}
