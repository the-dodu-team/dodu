package com.dodu.intervention;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable server-side record created for one accepted photo submission request.
 */
public record PhotoAttempt(
        UUID id,
        UUID promiseId,
        UUID clientRequestId,
        String requestFingerprint,
        String toolType,
        ProcessingStatus processingStatus,
        Instant requestReceivedAt) {

    public PhotoAttempt {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(promiseId, "promiseId must not be null");
        Objects.requireNonNull(clientRequestId, "clientRequestId must not be null");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint must not be null");
        Objects.requireNonNull(toolType, "toolType must not be null");
        Objects.requireNonNull(processingStatus, "processingStatus must not be null");
        Objects.requireNonNull(requestReceivedAt, "requestReceivedAt must not be null");
    }

    public static PhotoAttempt received(
            UUID promiseId,
            UUID clientRequestId,
            String requestFingerprint,
            String toolType,
            Instant requestReceivedAt) {
        return new PhotoAttempt(
                UUID.randomUUID(),
                promiseId,
                clientRequestId,
                requestFingerprint,
                toolType,
                ProcessingStatus.RECEIVED,
                requestReceivedAt);
    }

    public enum ProcessingStatus {
        RECEIVED,
        EVALUATING,
        COMPLETED,
        REJECTED,
        TECH_ERROR
    }
}
