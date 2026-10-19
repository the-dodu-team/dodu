package com.dodu.participant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.Participant;
import com.dodu.participant.domain.ParticipantSession;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.domain.SessionRevokeReason;
import com.dodu.participant.domain.SessionToken;
import com.dodu.participant.repository.ParticipantAccessRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 세션 인증의 흐름 검증. repository를 mock으로 바꿔 DB 없이 확인합니다.
 * 접근 판정 규칙 자체는 {@code SessionContextTest}가, SQL 동작은
 * {@link ParticipantAuthIntegrationTest}가 확인합니다.
 */
class ParticipantAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PARTICIPANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ParticipantAccessRepository repository;
    private SecretHasher hasher;
    private ParticipantAuthService service;
    private String token;

    @BeforeEach
    void setUp() {
        repository = mock(ParticipantAccessRepository.class);
        hasher = new SecretHasher(PEPPER);
        service = new ParticipantAuthService(repository, hasher, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
        token = SessionToken.generate(new SecureRandom());
    }

    @Test
    void missingOrMalformedHeaderIsAccessRequiredWithoutTouchingRepository() {
        for (String header : new String[] {null, "", "Bearer", "Basic " + token, "Bearer short", token}) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate(header));
            assertAccessRequired(e);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void unknownTokenIsAccessRequiredAndOnlyTheHashIsLookedUp() {
        when(repository.findSessionByTokenHash(any())).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + token));

        assertAccessRequired(e);
        verify(repository).findSessionByTokenHash(hasher.hash(token));
    }

    @Test
    void validSessionReturnsContextAndSkipsFreshActivityUpdate() {
        when(repository.findSessionByTokenHash(hasher.hash(token)))
                .thenReturn(Optional.of(context(true, null, NOW, null, null, LATER, null)));

        SessionContext result = service.authenticate("Bearer " + token);

        assertEquals(PARTICIPANT_ID, result.participant().id());
        assertEquals(SESSION_ID, result.session().id());
        assertEquals(LATER, result.expiresAt());
        assertEquals(false, result.participant().onboardingCompleted());
        verify(repository, never()).touchSession(any(), any(), any());
    }

    @Test
    void staleActivityIsUpdatedOnce() {
        Instant lastSeen = NOW.minusSeconds(120);
        when(repository.findSessionByTokenHash(hasher.hash(token)))
                .thenReturn(Optional.of(context(true, null, lastSeen, null, null, LATER, NOW.minusSeconds(5))));

        SessionContext result = service.authenticate("Bearer " + token);

        assertEquals(true, result.participant().onboardingCompleted());
        verify(repository).touchSession(SESSION_ID, NOW, NOW.minus(ParticipantSession.ACTIVITY_UPDATE_INTERVAL));
    }

    @Test
    void sessionReplacedByAnotherDevice() {
        when(repository.findSessionByTokenHash(any())).thenReturn(Optional.of(context(
                false, SessionRevokeReason.SESSION_REPLACED.dbValue(), NOW, null, null, LATER, null)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + token));

        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        assertEquals(ParticipantAuthService.SESSION_REPLACED, e.code());
        verify(repository, never()).touchSession(any(), any(), any());
    }

    @Test
    void revokedWithdrawnAndEndedAccessShareOneCode() {
        SessionContext[] revoked = {
                context(false, "ACCESS_REVOKED", NOW, null, null, LATER, null),
                context(true, null, NOW, NOW.minusSeconds(1), null, LATER, null),
                context(true, null, NOW, null, NOW.minusSeconds(1), LATER, null),
                context(true, null, NOW, null, null, LATER, null, NOW)
        };
        for (SessionContext session : revoked) {
            when(repository.findSessionByTokenHash(any())).thenReturn(Optional.of(session));

            BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + token));

            assertEquals(ErrorType.UNAUTHENTICATED, e.type());
            assertEquals(ParticipantAuthService.ACCESS_REVOKED, e.code());
        }
        verify(repository, never()).touchSession(any(), any(), any());
    }

    @Test
    void expiredSessionIsAccessRequired() {
        when(repository.findSessionByTokenHash(any())).thenReturn(Optional.of(context(
                true, null, NOW, null, null, NOW.minusSeconds(1), null)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + token));

        assertAccessRequired(e);
    }

    @Test
    void errorMessagesNeverContainTheToken() {
        when(repository.findSessionByTokenHash(any())).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + token));

        assertEquals(false, e.getMessage().contains(token));
    }

    private static void assertAccessRequired(BusinessException e) {
        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        assertEquals(ParticipantAuthService.ACCESS_REQUIRED, e.code());
    }

    private static SessionContext context(
            boolean active, String revokeReason, Instant lastSeenAt,
            Instant withdrawnAt, Instant accessDisabledAt, Instant sessionExpiresAt, Instant onboardingCompletedAt) {
        return context(active, revokeReason, lastSeenAt, withdrawnAt, accessDisabledAt, sessionExpiresAt,
                onboardingCompletedAt, LATER);
    }

    private static SessionContext context(
            boolean active, String revokeReason, Instant lastSeenAt,
            Instant withdrawnAt, Instant accessDisabledAt, Instant sessionExpiresAt, Instant onboardingCompletedAt,
            Instant accessEndsAt) {
        Participant participant = new Participant(PARTICIPANT_ID, withdrawnAt, accessDisabledAt, onboardingCompletedAt);
        ParticipantSession session = new ParticipantSession(
                SESSION_ID, PARTICIPANT_ID, active, revokeReason, lastSeenAt, sessionExpiresAt);
        return new SessionContext(participant, session, accessEndsAt);
    }
}