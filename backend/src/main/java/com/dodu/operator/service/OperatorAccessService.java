package com.dodu.operator.service;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.operator.domain.OperatorAction;
import com.dodu.operator.domain.OperatorResult;
import com.dodu.operator.repository.OperatorAccessRepository;
import com.dodu.operator.repository.OperatorAccessRepository.ParticipantRow;
import com.dodu.participant.domain.AccessCode;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionRevokeReason;
import com.dodu.participant.repository.ParticipantAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 운영자 작업: 접근 코드 발급·재발급·철회(PARTICIPANT_ACCESS_CONTRACT §5·§7·§9).
 *
 * <p>모든 작업은 하나의 트랜잭션에서 참가자 행을 먼저 잠그고 시작합니다. 입장·재발급·철회가 같은 참가자에게
 * 겹치지 않고 순서가 하나로 정해지게 하기 위해서입니다(입장 쪽 {@code findCredentialForUpdate}도 같은 행을
 * 잠급니다). 성공한 작업만 감사 로그에 남기며, 코드·토큰 원문은 어디에도 기록하지 않습니다.
 */

@Service
public class OperatorAccessService {

    public static final String OPERATOR_REF_INVALID = "OPERATOR_REF_INVALID";
    public static final String PARTICIPANT_NOT_FOUND = "PARTICIPANT_NOT_FOUND";
    public static final String PARTICIPANT_WITHDRAWN = "PARTICIPANT_WITHDRAWN";
    public static final String ACTIVE_CREDENTIAL_EXISTS = "ACTIVE_CREDENTIAL_EXISTS";
    public static final String NO_ACTIVE_CREDENTIAL = "NO_ACTIVE_CREDENTIAL";
    public static final String WITHDRAWAL_REQUESTED_AT_INVALID = "WITHDRAWAL_REQUESTED_AT_INVALID";

    private static final int MAX_OPERATOR_REF_LENGTH = 128;

    /** 발급 결과. {@code code}는 운영자에게 전달할 때만 쓰는 원문이라 문자열 변환에서 가립니다. */
    public record IssuedCredential(UUID credentialId, String code) {
        @Override
        public String toString() {
            return "IssuedCredential[credentialId=" + credentialId + ", code=***]";
        }
    }

    public record WithdrawResult(int endedSessions, int deactivatedSubscriptions) {
    }

    private final OperatorAccessRepository operatorRepository;
    private final ParticipantAccessRepository accessRepository;
    private final SecretHasher secretHasher;
    private final SecureRandom random;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OperatorAccessService(
            OperatorAccessRepository operatorRepository,
            ParticipantAccessRepository accessRepository,
            SecretHasher secretHasher,
            SecureRandom random,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.operatorRepository = operatorRepository;
        this.accessRepository = accessRepository;
        this.secretHasher = secretHasher;
        this.random = random;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 최초 발급. 활성 코드가 이미 있으면 거부합니다(재발급 요청 필요). */
    public IssuedCredential issue(UUID participantId, String operatorRef) {
        validateOperatorRef(operatorRef);
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            lockEligibleParticipant(participantId);
            if (operatorRepository.findActiveCredentialId(participantId).isPresent()) {
                throw new BusinessException(ErrorType.CONFLICT, ACTIVE_CREDENTIAL_EXISTS,
                        "이미 사용 중인 참여 코드가 있어요. 재발급을 요청해 주세요.");
            }
            IssuedCredential issued = createCredential(participantId, now, null);
            operatorRepository.insertAuditLog(OperatorAction.ISSUE_CREDENTIAL, participantId,
                    operatorRef, OperatorResult.SUCCESS,"{}", now);
            return issued;
        });
    }

    /**
     * 재발급(계약 §5). 기존 코드 폐기, 새 코드 발급, 세션·알림 구독 종료를 한 트랜잭션에서 처리합니다.
     * 노출 여부를 서버가 구분할 수 없으므로 열려 있는 세션은 항상 종료합니다.
     */
    public IssuedCredential reissue(UUID participantId, String operatorRef) {
        validateOperatorRef(operatorRef);
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            lockEligibleParticipant(participantId);
            UUID oldCredentialId = operatorRepository.findActiveCredentialId(participantId)
                    .orElseThrow(() -> new BusinessException(ErrorType.CONFLICT, NO_ACTIVE_CREDENTIAL,
                            "활성 접근 코드가 없어요. 최초 발급을 사용해 주세요."));

            operatorRepository.revokeCredential(oldCredentialId, now);
            IssuedCredential issued = createCredential(participantId, now, oldCredentialId);
            int endedSessions = accessRepository.endActiveSessions(
                    participantId, now, SessionRevokeReason.CREDENTIAL_REVOKED);
            int deactivated = accessRepository.deactivateActiveSubscriptions(participantId, now);

            operatorRepository.insertAuditLog(OperatorAction.REISSUE_CREDENTIAL, participantId,
                    operatorRef, OperatorResult.SUCCESS,
                    "{\"replacedCredentialId\":\"%s\",\"endedSessions\":%d,\"deactivatedSubscriptions\":%d}"
                            .formatted(oldCredentialId, endedSessions, deactivated),
                    now);
            return issued;
        });
    }

    /**
     * 철회 적용(계약 §7.2). {@code requestedAt}은 참가자가 외부 채널로 철회를 요청한 시각(운영자 입력)이고,
     * 반영 시각은 지금입니다. 요청 시각은 반영 시각보다 늦을 수 없습니다.
     */
    public WithdrawResult withdraw(UUID participantId, Instant requestedAt, String operatorRef) {
        validateOperatorRef(operatorRef);
        if (requestedAt == null) {
            throw new BusinessException(ErrorType.VALIDATION, WITHDRAWAL_REQUESTED_AT_INVALID,
                    "requestedAt", "철회 요청 시각을 입력해 주세요.");
        }
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            if (requestedAt.isAfter(now)) {
                throw new BusinessException(ErrorType.VALIDATION, WITHDRAWAL_REQUESTED_AT_INVALID,
                        "requestedAt", "철회 요청 시각은 현재보다 늦을 수 없어요.");
            }
            ParticipantRow participant = operatorRepository.lockParticipant(participantId)
                    .orElseThrow(() -> new BusinessException(ErrorType.NOT_FOUND,
                            PARTICIPANT_NOT_FOUND, "참가자를 찾을 수 없어요."));
            if (participant.withdrawnAt() != null) {
                throw new BusinessException(ErrorType.CONFLICT, PARTICIPANT_WITHDRAWN,
                        "이미 철회된 참가자예요.");
            }

            operatorRepository.markWithdrawn(participantId, requestedAt, now);
            operatorRepository.findActiveCredentialId(participantId)
                    .ifPresent(id -> operatorRepository.revokeCredential(id, now));
            int endedSessions = accessRepository.endActiveSessions(
                    participantId, now, SessionRevokeReason.ACCESS_REVOKED);
            int deactivated = accessRepository.deactivateActiveSubscriptions(participantId, now);

            // TODO(철회 후속, 계약 §7.2 4·5번): 활성 약속을 Withdrawn으로 종료하고 예약된 알림·자기보고를 무효화합니다. 약속·스케줄러 쪽 구현이 정해지면 같은 트랜잭션 안에서 호출하세요.

            operatorRepository.insertAuditLog(OperatorAction.WITHDRAW, participantId, operatorRef,
                    OperatorResult.SUCCESS,
                    "{\"requestedAt\":\"%s\",\"endedSessions\":%d,\"deactivatedSubscriptions\":%d}"
                            .formatted(requestedAt, endedSessions, deactivated),
                    now);
            return new WithdrawResult(endedSessions, deactivated);
        });
    }

    private IssuedCredential createCredential(UUID participantId, Instant now, UUID replacesCredentialId) {
        String code = AccessCode.generate(random);
        UUID credentialId = operatorRepository.insertCredential(
                participantId, secretHasher.hash(code), now, replacesCredentialId
        );
        return new IssuedCredential(credentialId, code);
    }

    /** 참가자 행을 잠그고, 철회·접근 중단 상태면 발급 계열 작업을 거부합니다. */
    private void lockEligibleParticipant(UUID participantId) {
        ParticipantRow participant = operatorRepository.lockParticipant(participantId)
                .orElseThrow(() -> new BusinessException(ErrorType.NOT_FOUND,
                        PARTICIPANT_NOT_FOUND, "참가자를 찾을 수 없어요."));
        if (participant.withdrawnAt() != null || participant.accessDisabledAt() != null) {
            throw new BusinessException(ErrorType.CONFLICT, PARTICIPANT_WITHDRAWN,
                    "철회했거나 접근이 중단된 참가자예요.");
        }
    }

    private static void validateOperatorRef(String operatorRef) {
        if (operatorRef == null || operatorRef.isBlank() || operatorRef.length() > MAX_OPERATOR_REF_LENGTH) {
            throw new BusinessException(ErrorType.VALIDATION, OPERATOR_REF_INVALID, "operatorRef",
                    "실행한 운영자의 이름을 입력해 주세요.");
        }
    }
}
