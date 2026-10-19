package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 세션 접근 판정 규칙과 실제 만료 시각 검증(계약 §3.3, §6). DB 없이 값만으로 확인합니다. */
class SessionContextTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final String REPLACED = SessionRevokeReason.SESSION_REPLACED.dbValue();

    @Test
    void activeSessionOfNormalParticipantIsAllowed() {
        assertEquals(SessionAccessResult.ALLOWED, active().check(NOW));
    }

    @Test
    void sessionIsExpiredExactlyAtExpiry() {
        assertEquals(SessionAccessResult.ALLOWED, context(true, null, NOW.plusSeconds(1), null, null, LATER).check(NOW));
        assertEquals(SessionAccessResult.EXPIRED, context(true, null, NOW, null, null, LATER).check(NOW));
        assertEquals(SessionAccessResult.EXPIRED, context(true, null, EARLIER, null, null, LATER).check(NOW));
    }

    @Test
    void sessionEndedByAnotherDeviceIsReplaced() {
        assertEquals(SessionAccessResult.REPLACED, context(false, REPLACED, LATER, null, null, LATER).check(NOW));
    }

    @Test
    void sessionEndedForAnyOtherReasonIsRevoked() {
        assertEquals(SessionAccessResult.REVOKED, context(false, "ACCESS_REVOKED", LATER, null, null, LATER).check(NOW));
        assertEquals(SessionAccessResult.REVOKED, context(false, null, LATER, null, null, LATER).check(NOW));
    }

    @Test
    void withdrawnParticipantIsRevokedEvenWithActiveSession() {
        assertEquals(SessionAccessResult.REVOKED, context(true, null, LATER, EARLIER, null, LATER).check(NOW));
    }

    @Test
    void accessDisabledParticipantIsRevokedEvenWithActiveSession() {
        assertEquals(SessionAccessResult.REVOKED, context(true, null, LATER, null, EARLIER, LATER).check(NOW));
    }

    @Test
    void accessIsRevokedExactlyAtAccessEnd() {
        assertEquals(SessionAccessResult.ALLOWED, context(true, null, LATER, null, null, NOW.plusSeconds(1)).check(NOW));
        assertEquals(SessionAccessResult.REVOKED, context(true, null, LATER, null, null, NOW).check(NOW));
        assertEquals(SessionAccessResult.REVOKED, context(true, null, LATER, null, null, EARLIER).check(NOW));
    }

    @Test
    void missingAccessEndDoesNotRevoke() {
        assertEquals(SessionAccessResult.ALLOWED, context(true, null, LATER, null, null, null).check(NOW));
    }

    @Test
    void participantStateWinsOverReplacedSession() {
        // 철회한 참가자의 옛 세션을 "다른 기기에서 이용 중"으로 안내하면 사실과 다릅니다.
        assertEquals(SessionAccessResult.REVOKED, context(false, REPLACED, LATER, EARLIER, null, LATER).check(NOW));
        assertEquals(SessionAccessResult.REVOKED, context(false, REPLACED, LATER, null, null, EARLIER).check(NOW));
    }

    @Test
    void endedSessionWinsOverExpiry() {
        assertEquals(SessionAccessResult.REPLACED, context(false, REPLACED, EARLIER, null, null, LATER).check(NOW));
    }

    @Test
    void expiresAtIsTheEarlierOfSessionExpiryAndAccessEnd() {
        // 접근 종료일을 앞당긴 경우와 늦춘 경우 모두 실제로 접근이 끝나는 시각을 돌려줍니다.
        assertEquals(LATER, context(true, null, LATER, null, null, LATER.plusSeconds(60)).expiresAt());
        assertEquals(LATER, context(true, null, LATER.plusSeconds(60), null, null, LATER).expiresAt());
        assertEquals(LATER, context(true, null, LATER, null, null, LATER).expiresAt());
    }

    @Test
    void expiresAtFallsBackToWhicheverValueExists() {
        assertEquals(LATER, context(true, null, LATER, null, null, null).expiresAt());
        assertEquals(LATER, context(true, null, null, null, null, LATER).expiresAt());
    }

    @Test
    void participantAndSessionAreRequired() {
        ParticipantSession session = new ParticipantSession(UUID.randomUUID(), UUID.randomUUID(), true, null, NOW, LATER);
        Participant participant = new Participant(UUID.randomUUID(), null, null, null);
        assertThrows(NullPointerException.class, () -> new SessionContext(null, session, LATER));
        assertThrows(NullPointerException.class, () -> new SessionContext(participant, null, LATER));
    }

    private static SessionContext active() {
        return context(true, null, LATER, null, null, LATER);
    }

    private static SessionContext context(
            boolean sessionActive, String revokeReason, Instant sessionExpiresAt,
            Instant withdrawnAt, Instant accessDisabledAt, Instant accessEndsAt) {
        UUID participantId = UUID.randomUUID();
        Participant participant = new Participant(participantId, withdrawnAt, accessDisabledAt, null);
        ParticipantSession session = new ParticipantSession(
                UUID.randomUUID(), participantId, sessionActive, revokeReason, NOW, sessionExpiresAt);
        return new SessionContext(participant, session, accessEndsAt);
    }
}