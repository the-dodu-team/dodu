package com.dodu.intervention;

import java.time.Instant;
import java.util.Objects;

/** Keeps post-deadline processing attached to an already accepted attempt. */
public final class PhotoRecoveryPolicy {
    public boolean canRecover(AttemptState state, Instant scheduledAt, Instant receivedAt, Instant now) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return state == AttemptState.EVALUATING
                && !receivedAt.isBefore(scheduledAt)
                && receivedAt.isBefore(scheduledAt.plus(InterventionWindowPolicy.AUTHENTICATION_WINDOW));
    }

    public enum AttemptState { EVALUATING, COMPLETED, REJECTED, TECH_FAILED, WITHDRAWN }
}
