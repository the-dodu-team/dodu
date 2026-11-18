package com.dodu.promise;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dodu.promise.domain.PromiseDailySlotPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 하루 1약속 규칙과 실패 약속 재생성을 처리한다.
 * 호출자는 PromiseLifecycleService.reconcile로 참가자 행을 잠근 같은 트랜잭션에서 사용한다.
 */
@Service
public class PromiseDailySlotService {
    private final JdbcTemplate jdbc;

    public PromiseDailySlotService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 날짜를 사용할 수 있으면 대체할 실패 약속 ID 목록(최근 예정순)을 돌려준다.
     * 이미 그 날짜를 사용하는 약속이 있으면 409 PROMISE_DATE_ALREADY_USED를 던진다.
     */
    public List<UUID> claim(UUID participantId, LocalDate date, UUID excludedPromiseId) {
        List<Slot> slots = jdbc.query("""
                        select id, lifecycle_status, final_outcome from promise
                         where participant_id = ? and scheduled_date_kst = ?
                           and superseded_by_promise_id is null
                         order by scheduled_at desc
                        """,
                (rs, i) -> new Slot(rs.getObject("id", UUID.class), rs.getString("lifecycle_status"), rs.getString("final_outcome")),
                participantId, date);
        List<UUID> replaceable = new java.util.ArrayList<>();
        for (Slot slot : slots) {
            if (slot.id().equals(excludedPromiseId)) continue;
            if (!PromiseDailySlotPolicy.isReplaceable(slot.lifecycleStatus(), slot.finalOutcome())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "PROMISE_DATE_ALREADY_USED");
            }
            replaceable.add(slot.id());
        }
        return replaceable;
    }

    /** 새 약속이 저장된 뒤 이전 실패 약속을 참가자 화면에서 제외한다. 원자료는 보존한다. */
    public void supersede(List<UUID> previous, UUID replacement, Instant now) {
        for (UUID id : previous) {
            jdbc.update("""
                    update promise set superseded_by_promise_id = ?, superseded_at = ?, updated_at = ?,
                           row_version = row_version + 1
                     where id = ? and superseded_by_promise_id is null
                    """, replacement, Timestamp.from(now), Timestamp.from(now), id);
        }
    }

    private record Slot(UUID id, String lifecycleStatus, String finalOutcome) { }
}
