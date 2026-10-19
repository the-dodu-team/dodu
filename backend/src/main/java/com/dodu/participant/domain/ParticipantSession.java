package com.dodu.participant.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 참가자 세션의 상태(테이블 {@code participant_session}). 조회한 시점의 값을 담은 불변 객체입니다.
 * 세션은 참가자와 별개로 교체·종료되므로 {@link Participant}와 나눠 둡니다.
 *
 * @param id 세션 ID
 * @param participantId 세션이 가리키는 참가자
 * @param active 세션이 활성인지({@code lifecycle_status = 'Active'})
 * @param revokeReason 종료된 세션의 종료 사유. 활성이면 null
 * @param lastSeenAt 마지막 접속 시각
 * @param expiresAt 세션 만료 시각
 */
public record ParticipantSession(
        UUID id,
        UUID participantId,
        boolean active,
        String revokeReason,
        Instant lastSeenAt,
        Instant expiresAt) {

    /**
     * 마지막 접속 시각({@code last_seen_at})을 갱신하는 최소 간격. 요청마다 쓰기가 생기지 않게
     * 이 간격이 지났을 때만 갱신합니다(개발 결정).
     */
    public static final Duration ACTIVITY_UPDATE_INTERVAL = Duration.ofMinutes(1);

    public ParticipantSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
    }

    /** 다른 기기 입장으로 종료된 세션인지. */
    public boolean isReplaced() {
        return !active && SessionRevokeReason.SESSION_REPLACED.dbValue().equals(revokeReason);
    }

    /** 세션 만료 시각에 도달했는지. 만료 시각이 정확히 지금이면 만료입니다. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** 마지막 접속 시각을 갱신할 때가 됐는지. */
    public boolean isActivityStale(Instant now) {
        return lastSeenAt.isBefore(now.minus(ACTIVITY_UPDATE_INTERVAL));
    }
}