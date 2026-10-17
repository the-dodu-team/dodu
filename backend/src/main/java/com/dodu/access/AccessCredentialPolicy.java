package com.dodu.access;

import java.time.Instant;
import java.util.Objects;

/**
 * Server-side access rules for the closed participant flow.
 * Persistence and HTTP mapping are added in the next implementation step.
 */
public final class AccessCredentialPolicy {
    public enum Decision {
        ALLOWED,
        ACCESS_REQUIRED,
        CREDENTIAL_REVOKED,
        SESSION_REPLACED,
        STUDY_PERIOD_ENDED,
        PARTICIPANT_WITHDRAWN
    }

    public record AccessState(
            boolean credentialValid,
            boolean sessionActive,
            Instant sessionExpiresAt,
            boolean participantActive,
            Instant accessEndsAt) {
        public AccessState {
            Objects.requireNonNull(sessionExpiresAt, "sessionExpiresAt");
            Objects.requireNonNull(accessEndsAt, "accessEndsAt");
        }
    }

    public Decision decide(AccessState state, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        if (!state.participantActive()) {
            return Decision.PARTICIPANT_WITHDRAWN;
        }
        if (!now.isBefore(state.accessEndsAt())) {
            return Decision.STUDY_PERIOD_ENDED;
        }
        if (!state.credentialValid()) {
            return Decision.CREDENTIAL_REVOKED;
        }
        if (!state.sessionActive()) {
            return Decision.SESSION_REPLACED;
        }
        if (!now.isBefore(state.sessionExpiresAt())) {
            return Decision.ACCESS_REQUIRED;
        }
        return Decision.ALLOWED;
    }
}
