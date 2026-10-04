package com.dodu.intervention;

import java.util.Objects;
import java.util.UUID;

/**
 * Separates network retries from new photo submissions before persistence.
 */
public final class PhotoAttemptIdentityPolicy {

    private PhotoAttemptIdentityPolicy() {
    }

    public static AttemptDecision classify(
            UUID existingClientRequestId,
            String existingRequestFingerprint,
            UUID incomingClientRequestId,
            String incomingRequestFingerprint) {
        Objects.requireNonNull(existingClientRequestId, "existingClientRequestId must not be null");
        Objects.requireNonNull(existingRequestFingerprint, "existingRequestFingerprint must not be null");
        Objects.requireNonNull(incomingClientRequestId, "incomingClientRequestId must not be null");
        Objects.requireNonNull(incomingRequestFingerprint, "incomingRequestFingerprint must not be null");

        if (!existingClientRequestId.equals(incomingClientRequestId)) {
            return AttemptDecision.NEW_ATTEMPT;
        }
        if (!existingRequestFingerprint.equals(incomingRequestFingerprint)) {
            return AttemptDecision.CONFLICT;
        }
        return AttemptDecision.RETRY_EXISTING;
    }

    public enum AttemptDecision {
        RETRY_EXISTING,
        NEW_ATTEMPT,
        CONFLICT
    }
}
