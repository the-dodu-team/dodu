package com.dodu.intervention.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dodu.intervention.PhotoAttempt;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoAttemptRetryPolicyTests {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PROMISE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void nonOwnerCannotDistinguishExistingRetryFromConflictOrMissingAttempt() {
        for (String fingerprint : new String[] {"photo-a", "photo-b"}) {
            assertEquals(PhotoAttemptRetryPolicy.Decision.FORBIDDEN,
                    PhotoAttemptRetryPolicy.classify(OTHER, OWNER, PROMISE, false,
                            attempt(PROMISE), REQUEST, fingerprint));
        }
        assertEquals(PhotoAttemptRetryPolicy.Decision.FORBIDDEN,
                PhotoAttemptRetryPolicy.classify(OTHER, OWNER, PROMISE, false, null, REQUEST, "photo-a"));
    }

    @Test
    void withdrawalBlocksBothRetryAndNewAttemptClassification() {
        assertEquals(PhotoAttemptRetryPolicy.Decision.PARTICIPANT_WITHDRAWN,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, true,
                        attempt(PROMISE), REQUEST, "photo-a"));
        assertEquals(PhotoAttemptRetryPolicy.Decision.PARTICIPANT_WITHDRAWN,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, true, null, REQUEST, "photo-a"));
    }

    @Test
    void sameRequestAndFingerprintCannotReuseAnAttemptFromAnotherPromise() {
        assertEquals(PhotoAttemptRetryPolicy.Decision.ATTEMPT_PROMISE_MISMATCH,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, false,
                        attempt(OTHER), REQUEST, "photo-a"));
    }

    @Test
    void ownedPromiseSeparatesRetryConflictAndNewAttempt() {
        assertEquals(PhotoAttemptRetryPolicy.Decision.RETRY_EXISTING,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, false,
                        attempt(PROMISE), REQUEST, "photo-a"));
        assertEquals(PhotoAttemptRetryPolicy.Decision.CONFLICT,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, false,
                        attempt(PROMISE), REQUEST, "photo-b"));
        assertEquals(PhotoAttemptRetryPolicy.Decision.NEW_ATTEMPT,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, false,
                        attempt(PROMISE), OTHER, "photo-b"));
        assertEquals(PhotoAttemptRetryPolicy.Decision.NEW_ATTEMPT,
                PhotoAttemptRetryPolicy.classify(OWNER, OWNER, PROMISE, false, null, REQUEST, "photo-a"));
    }

    private static PhotoAttempt attempt(UUID promiseId) {
        return PhotoAttempt.received(promiseId, REQUEST, "photo-a", "computer",
                Instant.parse("2026-10-05T00:00:00Z"));
    }
}
