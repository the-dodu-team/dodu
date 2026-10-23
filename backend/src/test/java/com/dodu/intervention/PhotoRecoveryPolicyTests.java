package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhotoRecoveryPolicyTests {
    private static final Instant T = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    void acceptsOnlyAnAlreadyAcceptedAttemptEvenAfterTPlus30() {
        var policy = new PhotoRecoveryPolicy();
        assertTrue(policy.canRecover(PhotoRecoveryPolicy.AttemptState.EVALUATING,
                T, T.plusSeconds(60), T.plusSeconds(1900)));
        assertFalse(policy.canRecover(PhotoRecoveryPolicy.AttemptState.EVALUATING,
                T, T.plusSeconds(1800), T.plusSeconds(1900)));
    }

    @Test
    void doesNotRecoverCompletedRejectedOrWithdrawnAttempts() {
        var policy = new PhotoRecoveryPolicy();
        assertFalse(policy.canRecover(PhotoRecoveryPolicy.AttemptState.COMPLETED, T, T.plusSeconds(60), T));
        assertFalse(policy.canRecover(PhotoRecoveryPolicy.AttemptState.REJECTED, T, T.plusSeconds(60), T));
        assertFalse(policy.canRecover(PhotoRecoveryPolicy.AttemptState.WITHDRAWN, T, T.plusSeconds(60), T));
    }
}
