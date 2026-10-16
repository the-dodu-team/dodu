package com.dodu.intervention;

import java.time.Instant;
import java.util.Objects;

/** Separates new intervention work from already accepted attempt processing. */
public final class InterventionLifecycleGate {
    public Decision decide(Instant now, Instant interventionEndsAt, boolean acceptedBeforeEnd) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(interventionEndsAt, "interventionEndsAt must not be null");
        if (now.isBefore(interventionEndsAt)) return Decision.NEW_WORK_ALLOWED;
        return acceptedBeforeEnd ? Decision.ACCEPTED_WORK_ONLY : Decision.CLOSED;
    }

    public enum Decision { NEW_WORK_ALLOWED, ACCEPTED_WORK_ONLY, CLOSED }
}
