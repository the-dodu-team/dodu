package com.dodu.participant.repository;

import com.dodu.participant.domain.DeviceStatus;
import com.dodu.participant.domain.Participant;
import com.dodu.participant.domain.ParticipantSession;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.domain.SessionRevokeReason;
import com.dodu.participant.domain.SessionStatus;
import com.dodu.participant.domain.SubscriptionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 입장·세션 발급과 세션 인증에 필요한 저장 접근. 업무 규칙은 service에서 판단하고 여기서는 조회와 저장만 합니다.
 * 시각 값은 pgjdbc가 안전하게 다루는 {@link OffsetDateTime}(UTC)으로 바꿔 넘기고 읽습니다.
 */
@Repository
public class ParticipantAccessRepository {

    /** 입장 코드 해시로 찾은 자격과 참가자·연구 기간 상태. */
    public record CredentialLookup(
            UUID participantId,
            Instant credentialRevokedAt,
            Instant credentialExpiresAt,
            Instant withdrawnAt,
            Instant accessDisabledAt,
            Instant onboardingCompletedAt,
            Instant accessEndsAt) {
    }

    public record DeviceRow(UUID id, String lifecycleStatus) {
    }

    private final JdbcClient jdbc;

    public ParticipantAccessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 코드 해시로 자격을 찾고 참가자 행을 잠급니다({@code for update of p}).
     * 같은 참가자의 입장·철회·재발급 처리가 서로 겹치지 않게 직렬화하기 위해서입니다.
     */
    public Optional<CredentialLookup> findCredentialForUpdate(String secretHash) {
        // 1) 잠그기 전에 어느 참가자의 코드인지만 찾습니다.
        Optional<UUID> participantId = jdbc.sql("""
                    select participant_id from access_credential where secret_hash = :hash
                    """)
                .param("hash", secretHash)
                .query(UUID.class)
                .optional();
        if (participantId.isEmpty()) {
            return Optional.empty();
        }

        // 2) 참가자 행을 잠급니다. 다른 트랜잭션이 잠그고 있으면 끝날 때까지 기다립니다.
        jdbc.sql("select id from participant where id = :id for update")
                .param("id", participantId.get())
                .query(UUID.class)
                .optional();

        // 3) 잠근 뒤에 다시 읽습니다. 기다리는 동안 재발급·철회가 커밋됐어도 최신 값이 보입니다.
        return jdbc.sql("""
                    select p.id as participant_id,
                           c.revoked_at as credential_revoked_at,
                           c.expires_at as credential_expires_at,
                           p.withdrawn_at,
                           p.access_disabled_at,
                           p.onboarding_completed_at,
                           s.access_ends_at
                    from access_credential c
                    join participant p on p.id = c.participant_id
                    join study_period s on s.id = p.study_period_id
                    where c.secret_hash = :hash
                    """)
                .param("hash", secretHash)
                .query((rs, rowNum) -> new CredentialLookup(
                        rs.getObject("participant_id", UUID.class),
                        instant(rs, "credential_revoked_at"),
                        instant(rs, "credential_expires_at"),
                        instant(rs, "withdrawn_at"),
                        instant(rs, "access_disabled_at"),
                        instant(rs, "onboarding_completed_at"),
                        instant(rs, "access_ends_at")))
                .optional();
    }

    public Optional<DeviceRow> findDevice(UUID participantId, String deviceKeyHash) {
        return jdbc.sql("""
                        select id, lifecycle_status
                        from participant_device
                        where participant_id = :participantId and device_key_hash = :hash
                        """)
                .param("participantId", participantId)
                .param("hash", deviceKeyHash)
                .query((rs, rowNum) -> new DeviceRow(
                        rs.getObject("id", UUID.class), rs.getString("lifecycle_status")))
                .optional();
    }

    public UUID insertDevice(UUID participantId, String deviceKeyHash, Instant now) {
        return jdbc.sql("""
                        insert into participant_device
                            (participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at)
                        values (:participantId, :hash, :status, :now, :now)
                        returning id
                        """)
                .param("participantId", participantId)
                .param("hash", deviceKeyHash)
                .param("status", DeviceStatus.ACTIVE.dbValue())
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    /** 기존 기기를 다시 활성화하고 마지막 접속 시각을 갱신합니다. */
    public void touchDevice(UUID deviceId, Instant now) {
        jdbc.sql("""
                        update participant_device
                        set lifecycle_status = :status, last_seen_at = :now, deactivated_at = null
                        where id = :id
                        """)
                .param("status", DeviceStatus.ACTIVE.dbValue())
                .param("now", utc(now))
                .param("id", deviceId)
                .update();
    }

    /** 입장한 기기 외의 활성 기기를 비활성화합니다. 참가자당 활성 기기는 1개입니다. */
    public int deactivateOtherDevices(UUID participantId, UUID keepDeviceId, Instant now) {
        return jdbc.sql("""
                        update participant_device
                        set lifecycle_status = :inactive, deactivated_at = :now
                        where participant_id = :participantId
                          and id <> :keepId
                          and lifecycle_status = :active
                        """)
                .param("inactive", DeviceStatus.INACTIVE.dbValue())
                .param("now", utc(now))
                .param("participantId", participantId)
                .param("keepId", keepDeviceId)
                .param("active", DeviceStatus.ACTIVE.dbValue())
                .update();
    }

    /** 참가자의 활성 세션을 모두 종료합니다. @return 종료한 세션 수 */
    public int endActiveSessions(UUID participantId, Instant now, SessionRevokeReason reason) {
        return jdbc.sql("""
                        update participant_session
                        set lifecycle_status = :ended, revoked_at = :now, revoke_reason = :reason
                        where participant_id = :participantId and lifecycle_status = :active
                        """)
                .param("ended", SessionStatus.ENDED.dbValue())
                .param("now", utc(now))
                .param("reason", reason.dbValue())
                .param("participantId", participantId)
                .param("active", SessionStatus.ACTIVE.dbValue())
                .update();
    }

    /** 참가자의 활성 알림 구독을 모두 비활성화합니다. */
    public int deactivateActiveSubscriptions(UUID participantId, Instant now) {
        return jdbc.sql("""
                        update notification_subscription
                        set lifecycle_status = :inactive, deactivated_at = :now
                        where participant_id = :participantId and lifecycle_status = :active
                        """)
                .param("inactive", SubscriptionStatus.INACTIVE.dbValue())
                .param("now", utc(now))
                .param("participantId", participantId)
                .param("active", SubscriptionStatus.ACTIVE.dbValue())
                .update();
    }

    public UUID insertSession(
            UUID participantId, UUID deviceId, String tokenHash, Instant now, Instant expiresAt) {
        return jdbc.sql("""
                        insert into participant_session
                            (participant_id, participant_device_id, session_token_hash,
                             lifecycle_status, issued_at, last_seen_at, expires_at)
                        values (:participantId, :deviceId, :tokenHash, :status, :now, :now, :expiresAt)
                        returning id
                        """)
                .param("participantId", participantId)
                .param("deviceId", deviceId)
                .param("tokenHash", tokenHash)
                .param("status", SessionStatus.ACTIVE.dbValue())
                .param("now", utc(now))
                .param("expiresAt", utc(expiresAt))
                .query(UUID.class)
                .single();
    }

    /**
     * 세션 토큰 해시로 세션·참가자·연구 기간의 접근 종료 시각을 한 번에 읽습니다. 요청마다 호출되므로 조회만 하고
     * 잠금은 걸지 않습니다({@code session_token_hash}에는 유니크 인덱스가 있습니다).
     */
    public Optional<SessionContext> findSessionByTokenHash(String tokenHash) {
        return jdbc.sql("""
                        select s.id as session_id,
                               s.participant_id,
                               s.lifecycle_status,
                               s.revoke_reason,
                               s.last_seen_at,
                               s.expires_at,
                               p.withdrawn_at,
                               p.access_disabled_at,
                               p.onboarding_completed_at,
                               sp.access_ends_at
                        from participant_session s
                        join participant p on p.id = s.participant_id
                        join study_period sp on sp.id = p.study_period_id
                        where s.session_token_hash = :hash
                        """)
                .param("hash", tokenHash)
                .query((rs, rowNum) -> {
                    UUID participantId = rs.getObject("participant_id", UUID.class);
                    Participant participant = new Participant(
                            participantId,
                            instant(rs, "withdrawn_at"),
                            instant(rs, "access_disabled_at"),
                            instant(rs, "onboarding_completed_at"));
                    ParticipantSession session = new ParticipantSession(
                            rs.getObject("session_id", UUID.class),
                            participantId,
                            SessionStatus.ACTIVE.dbValue().equals(rs.getString("lifecycle_status")),
                            rs.getString("revoke_reason"),
                            instant(rs, "last_seen_at"),
                            instant(rs, "expires_at"));
                    return new SessionContext(participant, session, instant(rs, "access_ends_at"));
                })
                .optional();
    }

    /**
     * 세션의 마지막 접속 시각을 갱신합니다. {@code staleBefore}보다 오래된 경우에만 갱신해서, 같은 세션의
     * 요청이 몰려도 쓰기는 한 번만 일어납니다.
     */
    public int touchSession(UUID sessionId, Instant now, Instant staleBefore) {
        return jdbc.sql("""
                        update participant_session
                        set last_seen_at = :now
                        where id = :id and last_seen_at < :staleBefore
                        """)
                .param("now", utc(now))
                .param("id", sessionId)
                .param("staleBefore", utc(staleBefore))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}