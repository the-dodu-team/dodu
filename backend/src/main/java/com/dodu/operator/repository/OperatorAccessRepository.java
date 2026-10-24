package com.dodu.operator.repository;

import com.dodu.operator.domain.OperatorAction;
import com.dodu.operator.domain.OperatorResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;


/**
 * 운영자 작업(코드 발급·재발급·철회, 계약 §5·§7·§9)에 필요한 저장 접근. 업무 규칙은 service에서 판단하고
 * 여기서는 조회와 저장만 합니다. 세션 종료·구독 비활성화는 {@link com.dodu.participant.repository.ParticipantAccessRepository}를 씁니다.
 */
@Repository
public class OperatorAccessRepository {

    /** 잠금을 건 참가자 행. */
    public record ParticipantRow(UUID id, Instant withdrawnAt, Instant accessDisabledAt) {
    }

    private final JdbcClient jdbc;

    public OperatorAccessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 참가자 행을 잠급니다({@code for update}). 입장·재발급·철회가 같은 참가자에게 겹치지 않도록
     * 직렬화하는 기준 잠금이며, 입장 쪽 {@code findCredentialForUpdate}도 같은 행을 잠급니다.
     */
    public Optional<ParticipantRow> lockParticipant(UUID participantId) {
        return jdbc.sql("""
                        select id, withdrawn_at, access_disabled_at
                        from participant
                        where id = :id
                        for update
                        """)
                .param("id", participantId)
                .query((rs, rowNum) -> new ParticipantRow(
                        rs.getObject("id", UUID.class),
                        instant(rs, "withdrawn_at"),
                        instant(rs, "access_disabled_at")
                )).optional();
    }

    /** 참가자의 활성(폐기되지 않은) 접근 자격 id. 부분 유니크 인덱스 때문에 최대 1개입니다. */
    public Optional<UUID> findActiveCredentialId(UUID participantId) {
        return jdbc.sql("""
                        select id
                        from access_credential
                        where participant_id = :participantId and revoked_at is null
                        """)
                .param("participantId", participantId)
                .query(UUID.class)
                .optional();
    }

    /** 접근 자격을 폐기합니다. 이미 폐기된 자격이면 0을 돌려줍니다. */
    public int revokeCredential(UUID credentialId, Instant now) {
        return jdbc.sql("""
                        update access_credential
                        set revoked_at = :now
                        where id = :id and revoked_at is null
                        """)
                .param("now", utc(now))
                .param("id", credentialId)
                .update();
    }

    /**
     * 참가자를 철회 상태로 바꿉니다. 요청 시각은 운영자가 입력한 값이고(데이터 제외 기준), 반영 시각은
     * {@code now}입니다(감사용, 계약 §7.2). 이미 철회된 참가자면 0을 돌려줍니다.
     */
    public int markWithdrawn(UUID participantId, Instant requestedAt, Instant now) {
        return jdbc.sql("""
                        update participant
                        set withdrawal_requested_at = :requestedAt,
                            withdrawn_at = :now,
                            access_disabled_at = :now,
                            updated_at = :now
                        where id = :id and withdrawn_at is null
                        """)
                .param("requestedAt", utc(requestedAt))
                .param("now", utc(now))
                .param("id", participantId)
                .update();
    }

    /**
     * 운영자 작업 감사 기록을 남깁니다. {@code detailJson}에는 코드·토큰 원문을 넣지 않습니다.
     * 참가자를 찾지 못한 거부 기록도 남길 수 있게 {@code participantId}는 null을 허용합니다.
     */
    public void insertAuditLog(
            OperatorAction action,
            UUID participantId,
            String operatorRef,
            OperatorResult result,
            String detailJson,
            Instant now) {
        jdbc.sql("""
                        insert into operator_audit_log
                            (action_type, participant_id, operator_ref, result, detail, occurred_at)
                        values (:action, :participantId, :operatorRef, :result, cast(:detail as jsonb), :now)
                        """)
                .param("action", action.dbValue())
                .param("participantId", participantId)
                .param("operatorRef", operatorRef)
                .param("result", result.dbValue())
                .param("detail", detailJson)
                .param("now", utc(now))
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
