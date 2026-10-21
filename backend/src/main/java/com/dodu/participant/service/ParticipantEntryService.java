package com.dodu.participant.service;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.AccessCode;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionRevokeReason;
import com.dodu.participant.domain.SessionToken;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.repository.ParticipantAccessRepository.CredentialLookup;
import com.dodu.participant.repository.ParticipantAccessRepository.DeviceRow;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 참가자 입장과 세션 발급(PARTICIPANT_ACCESS_CONTRACT §3).
 *
 * <p>코드가 무효·만료·폐기됐거나 참가자가 철회·접근 종료 상태이면 사유를 구분하지 않고 같은 오류
 * ({@value #INVALID_CODE})를 돌려줍니다. 사유를 알려 주면 코드 대입 공격에 정보를 주기 때문입니다.
 * 철회한 참가자에게 보여줄 화면은 미정이라(계약 §7.4, 디자인 D3) 일단 같은 응답으로 둡니다.
 *
 * <p>트랜잭션 안에서 참가자 행을 잠가 같은 참가자의 동시 입장·철회를 직렬화하고, "참가자당 활성
 * 세션 1개" 부분 유니크 인덱스를 마지막 방어선으로 씁니다. 인덱스 위반이 나면 한 번 다시 시도하고,
 * 그래도 실패하면 {@value #ENTRY_CONFLICT}로 응답합니다(계약 §3.3).
 */
@Service
public class ParticipantEntryService {

    public static final String INVALID_CODE = "ACCESS_CODE_INVALID";
    public static final String ENTRY_CONFLICT = "ENTRY_CONFLICT";
    public static final String STUDY_PERIOD_ENDED = "STUDY_PERIOD_ENDED";
    public static final String DEVICE_KEY_INVALID = "DEVICE_KEY_INVALID";

    static final int MIN_DEVICE_KEY_LENGTH = 16;
    static final int MAX_DEVICE_KEY_LENGTH = 128;
    private static final int MAX_ATTEMPTS = 2;

    /** 입장 결과. {@code sessionToken}은 이 시점에만 원문으로 존재합니다. */
    public record EnterResult(String sessionToken, Instant expiresAt, boolean onboardingCompleted) {
    }

    private final ParticipantAccessRepository repository;
    private final SecretHasher secretHasher;
    private final SecureRandom random;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ParticipantEntryService(
            ParticipantAccessRepository repository,
            SecretHasher secretHasher,
            SecureRandom random,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.secretHasher = secretHasher;
        this.random = random;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public EnterResult enter(String rawCode, String deviceKey) {
        validateDeviceKey(deviceKey);
        Optional<String> code = AccessCode.normalize(rawCode);
        if (code.isEmpty()) {
            throw invalidCode();
        }
        String codeHash = secretHasher.hash(code.get());
        String deviceKeyHash = secretHasher.hash(deviceKey);

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> enterInTransaction(codeHash, deviceKeyHash));
            } catch (DuplicateKeyException e) {
                // 동시에 활성 세션이 둘 생기려던 경우. 한 번만 다시 시도합니다.
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorType.CONFLICT, ENTRY_CONFLICT,
                            "잠시 후 다시 시도해 주세요.");
                }
            }
        }
    }

    private EnterResult enterInTransaction(String codeHash, String deviceKeyHash) {
        Instant now = Instant.now(clock);

        CredentialLookup credential = repository.findCredentialForUpdate(codeHash)
                .orElseThrow(ParticipantEntryService::invalidCode);
        if (credential.credentialRevokedAt() != null
                || isReached(credential.credentialExpiresAt(), now)
                || credential.withdrawnAt() != null
                || credential.accessDisabledAt() != null) {
            throw invalidCode();
        }
        Instant accessEndsAt = credential.accessEndsAt();
        if (accessEndsAt == null) {
            // 연구 기간의 접근 종료 시각이 설정되지 않은 환경. 상한 없는 세션을 만들지 않고 실패시킵니다.
            throw new BusinessException(ErrorType.TECHNICAL, "INTERNAL_ERROR",
                    "일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.");
        }
        if (isReached(accessEndsAt, now)) {
            throw new BusinessException(ErrorType.CONFLICT, STUDY_PERIOD_ENDED, "행동검증이 종료됐어요.");
        }

        UUID participantId = credential.participantId();
        UUID deviceId = upsertDevice(participantId, deviceKeyHash, now);

        // 새 기기 입장: 기존 세션·알림 구독·다른 기기를 종료합니다(0928 정책).
        repository.endActiveSessions(participantId, now, SessionRevokeReason.SESSION_REPLACED);
        repository.deactivateActiveSubscriptions(participantId, now);
        repository.deactivateOtherDevices(participantId, deviceId, now);

        String token = SessionToken.generate(random);
        repository.insertSession(participantId, deviceId, secretHasher.hash(token), now, accessEndsAt);

        return new EnterResult(token, accessEndsAt, credential.onboardingCompletedAt() != null);
    }

    private UUID upsertDevice(UUID participantId, String deviceKeyHash, Instant now) {
        Optional<DeviceRow> existing = repository.findDevice(participantId, deviceKeyHash);
        if (existing.isPresent()) {
            repository.touchDevice(existing.get().id(), now);
            return existing.get().id();
        }
        return repository.insertDevice(participantId, deviceKeyHash, now);
    }

    private static void validateDeviceKey(String deviceKey) {
        if (deviceKey == null
                || deviceKey.length() < MIN_DEVICE_KEY_LENGTH
                || deviceKey.length() > MAX_DEVICE_KEY_LENGTH) {
            throw new BusinessException(ErrorType.VALIDATION, DEVICE_KEY_INVALID, "deviceKey",
                    "기기 정보를 확인할 수 없어요. 앱을 다시 열어 주세요.");
        }
    }

    private static boolean isReached(Instant limit, Instant now) {
        return limit != null && !now.isBefore(limit);
    }

    private static BusinessException invalidCode() {
        return new BusinessException(ErrorType.UNAUTHENTICATED, INVALID_CODE, "유효하지 않거나 만료된 코드예요.");
    }
}