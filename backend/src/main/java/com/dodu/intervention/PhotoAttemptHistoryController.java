package com.dodu.intervention;

import com.dodu.participant.service.ParticipantAuthService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 참가자 본인의 사진 인증 시도 이력만 조회한다. 사진 원문과 접근정보는 응답하지 않는다. */
@RestController
@RequestMapping("/api/promises/{promiseId}/auth-attempts")
public class PhotoAttemptHistoryController {
    private final JdbcTemplate jdbc;
    private final ParticipantAuthService authService;

    public PhotoAttemptHistoryController(JdbcTemplate jdbc, ParticipantAuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    @GetMapping
    public List<AttemptHistoryResponse> list(@PathVariable UUID promiseId,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID participantId = authService.authenticate(authorization).participant().id();
        Boolean ownsPromise = jdbc.query("""
                select exists(
                    select 1 from promise where id = ? and participant_id = ?
                )
                """, rs -> rs.next() && rs.getBoolean(1), promiseId, participantId);
        if (!Boolean.TRUE.equals(ownsPromise)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        }
        return jdbc.query("""
                select id, tool_type, processing_status, decision_result, rejection_reason,
                       technical_error_type, model_version, request_received_at,
                       valid_received_at, decided_at
                  from auth_attempt
                 where promise_id = ?
                 order by request_received_at desc, id desc
                """, (rs, rowNum) -> new AttemptHistoryResponse(
                rs.getObject("id", UUID.class),
                rs.getString("tool_type"),
                rs.getString("processing_status"),
                rs.getString("decision_result"),
                rs.getString("rejection_reason"),
                rs.getString("technical_error_type"),
                rs.getString("model_version"),
                rs.getTimestamp("request_received_at").toInstant(),
                rs.getTimestamp("valid_received_at") == null ? null : rs.getTimestamp("valid_received_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant()), promiseId);
    }

    public record AttemptHistoryResponse(UUID attemptId, String toolType, String processingStatus,
                                         String decisionResult, String rejectionReason,
                                         String technicalErrorType, String modelVersion,
                                         Instant requestReceivedAt, Instant validReceivedAt,
                                         Instant decidedAt) { }
}
