package com.dodu.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessCredentialPolicyTests {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final Instant SESSION_END = Instant.parse("2026-10-17T00:00:00Z");
    private static final Instant ACCESS_END = Instant.parse("2026-11-01T00:00:00Z");

    private final AccessCredentialPolicy policy = new AccessCredentialPolicy();

    @Test
    void allowsOnlyActiveParticipantWithValidCredentialAndSession() {
        assertEquals(AccessCredentialPolicy.Decision.ALLOWED,
                policy.decide(new AccessCredentialPolicy.AccessState(true, true, SESSION_END, true, ACCESS_END), NOW));
    }

    @Test
    void rejectsRevokedCredentialBeforeCheckingSession() {
        assertEquals(AccessCredentialPolicy.Decision.CREDENTIAL_REVOKED,
                policy.decide(new AccessCredentialPolicy.AccessState(false, true, SESSION_END, true, ACCESS_END), NOW));
    }

    @Test
    void distinguishesReplacementFromExpiredSession() {
        assertEquals(AccessCredentialPolicy.Decision.SESSION_REPLACED,
                policy.decide(new AccessCredentialPolicy.AccessState(true, false, SESSION_END, true, ACCESS_END), NOW));
        assertEquals(AccessCredentialPolicy.Decision.ACCESS_REQUIRED,
                policy.decide(new AccessCredentialPolicy.AccessState(true, true, NOW, true, ACCESS_END), NOW));
    }

    @Test
    void blocksWithdrawnAndEndedParticipants() {
        assertEquals(AccessCredentialPolicy.Decision.PARTICIPANT_WITHDRAWN,
                policy.decide(new AccessCredentialPolicy.AccessState(true, true, SESSION_END, false, ACCESS_END), NOW));
        assertEquals(AccessCredentialPolicy.Decision.STUDY_PERIOD_ENDED,
                policy.decide(new AccessCredentialPolicy.AccessState(true, true, SESSION_END, true, NOW), NOW));
    }
}
