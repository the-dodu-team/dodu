package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 참가자 상태 판정 검증. DB 없이 값만으로 확인합니다. */
class ParticipantTest {

    private static final Instant T = Instant.parse("2026-10-20T03:00:00Z");

    @Test
    void normalParticipantIsNotBlocked() {
        Participant participant = new Participant(UUID.randomUUID(), null, null, null);

        assertFalse(participant.isWithdrawn());
        assertFalse(participant.isAccessDisabled());
        assertFalse(participant.isAccessBlocked());
    }

    @Test
    void withdrawnParticipantIsBlocked() {
        Participant participant = new Participant(UUID.randomUUID(), T, null, null);

        assertTrue(participant.isWithdrawn());
        assertTrue(participant.isAccessBlocked());
    }

    @Test
    void accessDisabledParticipantIsBlocked() {
        Participant participant = new Participant(UUID.randomUUID(), null, T, null);

        assertTrue(participant.isAccessDisabled());
        assertTrue(participant.isAccessBlocked());
    }

    @Test
    void onboardingCompletedFollowsTimestamp() {
        assertFalse(new Participant(UUID.randomUUID(), null, null, null).onboardingCompleted());
        assertTrue(new Participant(UUID.randomUUID(), null, null, T).onboardingCompleted());
    }

    @Test
    void idIsRequired() {
        assertThrows(NullPointerException.class, () -> new Participant(null, null, null, null));
    }
}