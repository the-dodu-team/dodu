package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoAttemptIdentityPolicyTests {

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void reusesAttemptForSameRequestAndFingerprint() {
        assertEquals(
                PhotoAttemptIdentityPolicy.AttemptDecision.RETRY_EXISTING,
                PhotoAttemptIdentityPolicy.classify(REQUEST_ID, "fingerprint-a", REQUEST_ID, "fingerprint-a"));
    }

    @Test
    void createsNewAttemptForDifferentRequestId() {
        assertEquals(
                PhotoAttemptIdentityPolicy.AttemptDecision.NEW_ATTEMPT,
                PhotoAttemptIdentityPolicy.classify(REQUEST_ID, "fingerprint-a", OTHER_REQUEST_ID, "fingerprint-a"));
    }

    @Test
    void rejectsSameRequestIdWithDifferentFingerprintAsConflict() {
        assertEquals(
                PhotoAttemptIdentityPolicy.AttemptDecision.CONFLICT,
                PhotoAttemptIdentityPolicy.classify(REQUEST_ID, "fingerprint-a", REQUEST_ID, "fingerprint-b"));
    }
}
