package com.dodu.intervention.domain;

import com.dodu.intervention.PhotoAttempt;
import com.dodu.intervention.PhotoAttemptIdentityPolicy;
import java.util.Objects;
import java.util.UUID;

/**
 * Classifies retry identity only after server-side ownership checks.
 * The caller supplies an authenticated participant and locked server records.
 * NEW_ATTEMPT does not authorize submission: time/state checks and atomic persistence remain required.
 */
public final class PhotoAttemptRetryPolicy {
    private PhotoAttemptRetryPolicy() {
    }

    public static Decision classify(
            UUID authenticatedParticipantId,
            UUID promiseOwnerId,
            UUID promiseId,
            boolean participantWithdrawn,
            PhotoAttempt existingAttempt,
            UUID incomingRequestId,
            String incomingFingerprint) {
        Objects.requireNonNull(authenticatedParticipantId, "authenticatedParticipantId must not be null");
        Objects.requireNonNull(promiseOwnerId, "promiseOwnerId must not be null");
        Objects.requireNonNull(promiseId, "promiseId must not be null");
        Objects.requireNonNull(incomingRequestId, "incomingRequestId must not be null");
        Objects.requireNonNull(incomingFingerprint, "incomingFingerprint must not be null");
        if (!authenticatedParticipantId.equals(promiseOwnerId)) {
            return Decision.FORBIDDEN;
        }
        if (participantWithdrawn) {
            return Decision.PARTICIPANT_WITHDRAWN;
        }
        if (existingAttempt == null) {
            return Decision.NEW_ATTEMPT;
        }
        if (!promiseId.equals(existingAttempt.promiseId())) {
            return Decision.ATTEMPT_PROMISE_MISMATCH;
        }
        return switch (PhotoAttemptIdentityPolicy.classify(
                existingAttempt.clientRequestId(), existingAttempt.requestFingerprint(),
                incomingRequestId, incomingFingerprint)) {
            case RETRY_EXISTING -> Decision.RETRY_EXISTING;
            case CONFLICT -> Decision.CONFLICT;
            case NEW_ATTEMPT -> Decision.NEW_ATTEMPT;
        };
    }

    public enum Decision {
        FORBIDDEN,
        PARTICIPANT_WITHDRAWN,
        ATTEMPT_PROMISE_MISMATCH,
        RETRY_EXISTING,
        CONFLICT,
        NEW_ATTEMPT
    }
}
