package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoAttemptTests {

    @Test
    void createsReceivedAttemptWithServerReceiptMetadata() {
        UUID promiseId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-10-03T00:00:00Z");

        PhotoAttempt attempt = PhotoAttempt.received(
                promiseId, clientRequestId, "fingerprint-a", "BOOK", receivedAt);

        assertNotNull(attempt.id());
        assertEquals(promiseId, attempt.promiseId());
        assertEquals(clientRequestId, attempt.clientRequestId());
        assertEquals("fingerprint-a", attempt.requestFingerprint());
        assertEquals("BOOK", attempt.toolType());
        assertEquals(PhotoAttempt.ProcessingStatus.RECEIVED, attempt.processingStatus());
        assertEquals(receivedAt, attempt.requestReceivedAt());
    }
}
