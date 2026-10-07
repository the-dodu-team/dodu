package com.dodu.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeFailureWindowTests {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private final CodeFailureWindow policy = new CodeFailureWindow();

    @Test
    void tenthFailureBlocksForFifteenMinutes() {
        var state = CodeFailureWindow.State.empty();
        for (int i = 0; i < 9; i++) {
            var result = policy.failedCode(state, NOW);
            assertFalse(result.blocked());
            state = result.state();
        }
        var result = policy.failedCode(state, NOW);
        assertTrue(result.blocked());
        assertEquals(900, result.retryAfterSeconds());
        assertEquals(NOW.plusSeconds(900), result.state().blockedUntil());
    }

    @Test
    void blockedRequestsDoNotExtendExpiryAndExactExpiryAllowsAccess() {
        var state = new CodeFailureWindow.State(List.of(), NOW.plusSeconds(900));
        var result = policy.failedCode(state, NOW.plusSeconds(899).plusNanos(1));
        assertEquals(state, result.state());
        assertEquals(1, result.retryAfterSeconds());
        assertFalse(policy.inspect(state, NOW.plusSeconds(900)).blocked());
        assertEquals(1, policy.failedCode(state, NOW.plusSeconds(900)).state().failures().size());
    }

    @Test
    void tenMinuteWindowExcludesExactExpiredTimestamp() {
        var state = new CodeFailureWindow.State(
                List.of(NOW.minusSeconds(600), NOW.minusSeconds(599)), null);
        assertEquals(List.of(NOW.minusSeconds(599)), policy.inspect(state, NOW).state().failures());
        assertEquals(2, policy.failedCode(state, NOW).state().failures().size());
    }

    @Test
    void immutableStateCannotBeChangedByCaller() {
        var state = policy.failedCode(CodeFailureWindow.State.empty(), NOW).state();
        assertThrows(UnsupportedOperationException.class, () -> state.failures().clear());
        assertEquals(CodeFailureWindow.State.empty(), policy.inspect(state, NOW.plusSeconds(600)).state());
    }
}
