package com.dodu.participant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.AccessCode;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionRevokeReason;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.repository.ParticipantAccessRepository.CredentialLookup;
import com.dodu.participant.repository.ParticipantAccessRepository.DeviceRow;
import com.dodu.participant.service.ParticipantEntryService.EnterResult;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 입장 업무 규칙의 분기 검증. repository를 mock으로 바꿔 DB 없이 확인합니다.
 * SQL 동작(행 잠금, 유니크 인덱스, 일괄 종료)은 {@link ParticipantEntryIntegrationTest}가 확인합니다.
 */
class ParticipantEntryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final Instant FAR_FUTURE = NOW.plusSeconds(30L * 24 * 3600);
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();
    private static final String DEVICE = "device-key-aaaaaaaaaaaaaaaa";
    private static final String CODE = "ABCDEFGHJKMN";
    private static final UUID PARTICIPANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private ParticipantAccessRepository repository;
    private SecretHasher hasher;
    private ParticipantEntryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ParticipantAccessRepository.class);
        hasher = new SecretHasher(PEPPER);
        service = new ParticipantEntryService(
                repository,
                hasher,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")),
                mock(PlatformTransactionManager.class));
        when(repository.insertDevice(any(), anyString(), any())).thenReturn(DEVICE_ID);
        when(repository.insertSession(any(), any(), anyString(), any(), any())).thenReturn(UUID.randomUUID());
    }

    @Test
    void malformedCodeIsInvalidWithoutTouchingRepository() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.enter("not-a-code", DEVICE));

        assertInvalidCode(e);
        verifyNoInteractions(repository);
    }

    @Test
    void deviceKeyIsValidatedBeforeCode() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.enter("not-a-code", "short"));

        assertEquals(ErrorType.VALIDATION, e.type());
        assertEquals(ParticipantEntryService.DEVICE_KEY_INVALID, e.code());
        assertEquals("deviceKey", e.field());
    }

    @Test
    void unknownCodeIsInvalid() {
        when(repository.findCredentialForUpdate(anyString())).thenReturn(Optional.empty());

        assertInvalidCode(assertThrows(BusinessException.class, () -> service.enter(CODE, DEVICE)));
        verify(repository, never()).insertSession(any(), any(), anyString(), any(), any());
    }

    @Test
    void revokedExpiredWithdrawnAndDisabledGetTheSameError() {
        CredentialLookup[] blocked = {
                lookup(NOW.minusSeconds(1), null, null, null, FAR_FUTURE),   // 폐기된 코드
                lookup(null, NOW, null, null, FAR_FUTURE),                   // 코드 만료 시각 = 지금(경계)
                lookup(null, null, NOW.minusSeconds(1), null, FAR_FUTURE),   // 철회
                lookup(null, null, null, NOW.minusSeconds(1), FAR_FUTURE),   // 접근 종료
        };
        BusinessException first = null;
        for (CredentialLookup credential : blocked) {
            when(repository.findCredentialForUpdate(anyString())).thenReturn(Optional.of(credential));
            BusinessException e = assertThrows(BusinessException.class, () -> service.enter(CODE, DEVICE));
            assertInvalidCode(e);
            if (first == null) {
                first = e;
            }
            assertEquals(first.getMessage(), e.getMessage());
        }
        verify(repository, never()).insertSession(any(), any(), anyString(), any(), any());
    }

    @Test
    void accessEndExactlyNowIsRejectedButOneSecondBeforeIsAllowed() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, NOW)));
        BusinessException ended = assertThrows(BusinessException.class, () -> service.enter(CODE, DEVICE));
        assertEquals(ErrorType.CONFLICT, ended.type());
        assertEquals(ParticipantEntryService.STUDY_PERIOD_ENDED, ended.code());

        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, NOW.plusSeconds(1))));
        assertEquals(NOW.plusSeconds(1), service.enter(CODE, DEVICE).expiresAt());
    }

    @Test
    void missingAccessEndIsATechnicalError() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, null)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.enter(CODE, DEVICE));

        assertEquals(ErrorType.TECHNICAL, e.type());
        verify(repository, never()).insertSession(any(), any(), anyString(), any(), any());
    }

    @Test
    void successEndsOldSessionsThenStoresOnlyTokenHashWithAccessEndAsExpiry() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, FAR_FUTURE)));

        EnterResult result = service.enter(AccessCode.format(CODE), DEVICE);

        assertEquals(FAR_FUTURE, result.expiresAt());
        assertFalse(result.onboardingCompleted());
        verify(repository).findCredentialForUpdate(hasher.hash(CODE));
        verify(repository).endActiveSessions(PARTICIPANT_ID, NOW, SessionRevokeReason.SESSION_REPLACED);
        verify(repository).deactivateActiveSubscriptions(PARTICIPANT_ID, NOW);
        verify(repository).deactivateOtherDevices(PARTICIPANT_ID, DEVICE_ID, NOW);

        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertSession(
                any(), any(), tokenHash.capture(), any(), any());
        assertEquals(hasher.hash(result.sessionToken()), tokenHash.getValue());
        assertFalse(tokenHash.getValue().contains(result.sessionToken()));
    }

    @Test
    void onboardingCompletedFollowsParticipant() {
        when(repository.findCredentialForUpdate(anyString())).thenReturn(Optional.of(
                new CredentialLookup(PARTICIPANT_ID, null, null, null, null, NOW.minusSeconds(60), FAR_FUTURE)));

        assertTrue(service.enter(CODE, DEVICE).onboardingCompleted());
    }

    @Test
    void knownDeviceIsReusedNotInserted() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, FAR_FUTURE)));
        when(repository.findDevice(PARTICIPANT_ID, hasher.hash(DEVICE)))
                .thenReturn(Optional.of(new DeviceRow(DEVICE_ID, "Active")));

        service.enter(CODE, DEVICE);

        verify(repository).touchDevice(DEVICE_ID, NOW);
        verify(repository, never()).insertDevice(any(), anyString(), any());
    }

    @Test
    void retriesOnceWhenActiveSessionIndexIsViolated() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, FAR_FUTURE)));
        when(repository.insertSession(any(), any(), anyString(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenReturn(UUID.randomUUID());

        EnterResult result = service.enter(CODE, DEVICE);

        assertEquals(43, result.sessionToken().length());
        verify(repository, times(2)).findCredentialForUpdate(anyString());
    }

    @Test
    void gaveUpAfterSecondIndexViolationAsEntryConflict() {
        when(repository.findCredentialForUpdate(anyString()))
                .thenReturn(Optional.of(lookup(null, null, null, null, FAR_FUTURE)));
        when(repository.insertSession(any(), any(), anyString(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.enter(CODE, DEVICE));

        assertEquals(ErrorType.CONFLICT, e.type());
        assertEquals(ParticipantEntryService.ENTRY_CONFLICT, e.code());
        verify(repository, times(2)).findCredentialForUpdate(anyString());
    }

    private static void assertInvalidCode(BusinessException e) {
        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        assertEquals(ParticipantEntryService.INVALID_CODE, e.code());
    }

    /** 온보딩 미완료 상태의 참가자를 가정한 조회 결과. */
    private static CredentialLookup lookup(
            Instant credentialRevokedAt,
            Instant credentialExpiresAt,
            Instant withdrawnAt,
            Instant accessDisabledAt,
            Instant accessEndsAt) {
        return new CredentialLookup(
                PARTICIPANT_ID, credentialRevokedAt, credentialExpiresAt, withdrawnAt, accessDisabledAt, null,
                accessEndsAt);
    }
}