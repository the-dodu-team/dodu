package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 세션 상태 판정 검증. DB 없이 값만으로 확인합니다. */
class ParticipantSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final String REPLACED = SessionRevokeReason.SESSION_REPLACED.dbValue();

    @Test
    void endedSessionWithReplacementReasonIsReplaced() {
        assertTrue(session(false, REPLACED, NOW, NOW).isReplaced());
    }

    @Test
    void otherEndedOrActiveSessionIsNotReplaced() {
        assertFalse(session(false, "ACCESS_REVOKED", NOW, NOW).isReplaced());
        assertFalse(session(false, null, NOW, NOW).isReplaced());
        assertFalse(session(true, null, NOW, NOW).isReplaced());
        // 활성 세션에 종료 사유가 남아 있어도 교체된 세션으로 보지 않습니다.
        assertFalse(session(true, REPLACED, NOW, NOW).isReplaced());
    }

    @Test
    void sessionIsExpiredExactlyAtExpiry() {
        assertFalse(session(true, null, NOW, NOW.plusSeconds(1)).isExpired(NOW));
        assertTrue(session(true, null, NOW, NOW).isExpired(NOW));
        assertTrue(session(true, null, NOW, NOW.minusSeconds(1)).isExpired(NOW));
    }

    @Test
    void sessionWithoutExpiryIsNeverExpired() {
        assertFalse(session(true, null, NOW, null).isExpired(NOW));
    }

    @Test
    void activityIsStaleOnlyAfterTheUpdateInterval() {
        Instant interval = NOW.minus(ParticipantSession.ACTIVITY_UPDATE_INTERVAL);
        assertFalse(session(true, null, interval, NOW).isActivityStale(NOW));
        assertTrue(session(true, null, interval.minusSeconds(1), NOW).isActivityStale(NOW));
        assertFalse(session(true, null, NOW, NOW).isActivityStale(NOW));
    }

    @Test
    void requiredValuesAreChecked() {
        assertThrows(NullPointerException.class,
                () -> new ParticipantSession(null, UUID.randomUUID(), true, null, NOW, NOW));
        assertThrows(NullPointerException.class,
                () -> new ParticipantSession(UUID.randomUUID(), null, true, null, NOW, NOW));
        assertThrows(NullPointerException.class,
                () -> new ParticipantSession(UUID.randomUUID(), UUID.randomUUID(), true, null, null, NOW));
    }

    private static ParticipantSession session(
            boolean active, String revokeReason, Instant lastSeenAt, Instant expiresAt) {
        return new ParticipantSession(
                UUID.randomUUID(), UUID.randomUUID(), active, revokeReason, lastSeenAt, expiresAt);
    }
}