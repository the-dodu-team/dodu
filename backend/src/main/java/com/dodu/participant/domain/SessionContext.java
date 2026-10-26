package com.dodu.participant.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 세션 토큰 하나로 조회한 참가자·세션과 접근 종료 시각, 그리고 접근 판정 규칙(계약 §3.3, §6).
 *
 * <p>판정은 DB 없이 이 값만으로 하므로 규칙을 단위 테스트로 확인할 수 있습니다. 접근 종료 시각은
 * 조회하는 쪽(repository)이 정해서 넘깁니다. 정책 v2.5.2에서는 공식 개입 종료 이후 참가자별로
 * 마지막 자기보고 알림 + 24시간이 되며(SCRUM-63 연동 전까지는 연구 기간의 값), 어느 쪽이든
 * 판정 규칙은 같으므로 참가자·세션 객체에 복사하지 않고 여기서만 듭니다.
 *
 * @param accessEndsAt 접근 종료 시각. 정해지기 전이면 null
 */

public record SessionContext(Participant participant, ParticipantSession session, Instant accessEndsAt) {

    public SessionContext {
        Objects.requireNonNull(participant, "participant must not be null");
        Objects.requireNonNull(session, "session must not be null");
    }

    /**
     * 지금 이 세션으로 접근해도 되는지 판정합니다.
     *
     * <p>참가자·연구 기간 단위 상태(철회·접근 중단·접근 종료)를 세션 상태보다 먼저 봅니다. 철회한 참가자의 옛 세션이
     * "다른 기기에서 이용 중"으로 안내되면 사실과 다르기 때문입니다. 코드 폐기·재발급, 철회, 접근 종료는
     * 사유를 나누지 않고 모두 {@link SessionAccessResult#REVOKED}입니다(계약 §3.3).
     */
    public SessionAccessResult check(Instant now) {
        if (participant.isAccessBlocked() || isAccessEnded(now)) {
            return SessionAccessResult.REVOKED;
        }
        if (!session.active()) {
            return session.isReplaced() ? SessionAccessResult.REPLACED : SessionAccessResult.REVOKED;
        }
        if (session.isExpired(now)) {
            return SessionAccessResult.EXPIRED;
        }
        return SessionAccessResult.ALLOWED;
    }

    /**
     * 실제로 접근이 끝나는 시각. 세션은 발급 때의 접근 종료 시각으로 만료가 정해지므로, 운영자가 연구 기간
     * 종료일을 바꾸면 세션 만료와 어긋날 수 있어 둘 중 이른 값을 씁니다.
     */
    public Instant expiresAt() {
        Instant sessionExpiresAt = session.expiresAt();
        if (accessEndsAt == null) {
            return sessionExpiresAt;
        }
        if (sessionExpiresAt == null) {
            return accessEndsAt;
        }
        return sessionExpiresAt.isBefore(accessEndsAt) ? sessionExpiresAt : accessEndsAt;
    }

    private boolean isAccessEnded(Instant now) {
        return accessEndsAt != null && !now.isBefore(accessEndsAt);
    }
}