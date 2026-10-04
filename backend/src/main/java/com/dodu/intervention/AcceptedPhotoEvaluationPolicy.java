package com.dodu.intervention;

import java.time.Instant;
import java.util.Objects;

/**
 * Eligibility for applying an evaluation to a previously accepted submission.
 * All inputs must come from server records; request arrival alone is not valid acceptance.
 * The caller must check ownership and the current attempt in the same state transaction.
 */
public final class AcceptedPhotoEvaluationPolicy {
    private AcceptedPhotoEvaluationPolicy() {
    }

    public static Decision validate(
            Instant scheduledAt,
            Instant validReceivedAt,
            PhotoAttempt.ProcessingStatus processingStatus,
            boolean participantWithdrawn) {
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(processingStatus, "processingStatus must not be null");
        if (participantWithdrawn) {
            return Decision.PARTICIPANT_WITHDRAWN;
        }
        if (validReceivedAt == null) {
            return Decision.NOT_VALIDLY_RECEIVED;
        }
        if (validReceivedAt.isBefore(scheduledAt)
                || !validReceivedAt.isBefore(scheduledAt.plus(InterventionWindowPolicy.AUTHENTICATION_WINDOW))) {
            return Decision.OUTSIDE_SUBMISSION_WINDOW;
        }
        if (processingStatus != PhotoAttempt.ProcessingStatus.EVALUATING) {
            return Decision.NOT_EVALUATING;
        }
        return Decision.ALLOWED;
    }

    public enum Decision {
        ALLOWED,
        PARTICIPANT_WITHDRAWN,
        NOT_VALIDLY_RECEIVED,
        OUTSIDE_SUBMISSION_WINDOW,
        NOT_EVALUATING
    }
}
