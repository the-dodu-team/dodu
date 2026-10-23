package com.dodu.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromiseIdempotencyKeyPolicyTests {
    @Test
    void sameNormalizedRequestProducesSameFingerprint() {
        var first = new PromiseController.PromiseRequest(" task ", PromiseRequestValidator.ToolType.BOOK,
                "2026-10-20T20:00:00+09:00");
        var second = new PromiseController.PromiseRequest("task", PromiseRequestValidator.ToolType.BOOK,
                "2026-10-20T20:00:00+09:00");

        assertEquals(PromiseIdempotencyKeyPolicy.fingerprint(first), PromiseIdempotencyKeyPolicy.fingerprint(second));
    }

    @Test
    void controlCharactersAndBlankKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PromiseIdempotencyKeyPolicy.validate(" "));
        assertThrows(IllegalArgumentException.class, () -> PromiseIdempotencyKeyPolicy.validate("key\nvalue"));
        assertThrows(IllegalArgumentException.class, () -> PromiseIdempotencyKeyPolicy.validate("x".repeat(129)));
    }
}
