package com.dodu.intervention;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import com.dodu.intervention.domain.PhotoToolMatchPolicy;
import com.dodu.intervention.domain.Result;
import com.dodu.intervention.dto.ResultRequest;
import com.dodu.intervention.dto.ResultResponse;
import com.dodu.participant.service.ParticipantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@Tag(name = "사진 인증")
@RequestMapping("/api/promises/{promiseId}/photo-attempts/{attemptId}/result")
public class PhotoEvaluationResultController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ParticipantAuthService authService;

    @Autowired
    public PhotoEvaluationResultController(JdbcTemplate jdbc, ParticipantAuthService authService) {
        this(jdbc, authService, Clock.systemUTC());
    }

    PhotoEvaluationResultController(JdbcTemplate jdbc, ParticipantAuthService authService, Clock clock) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.clock = clock;
    }

    @Operation(summary = "사진 평가 결과 저장")
    @PostMapping
    @Transactional
    public ResultResponse save(@PathVariable UUID promiseId, @PathVariable UUID attemptId,
                               @RequestHeader("Authorization") String authorization,
                               @RequestBody ResultRequest request) {
        UUID participantId = participantFromBearer(authorization);
        if (request == null || request.result() == null) {
            throw error(HttpStatus.BAD_REQUEST, "RESULT_REQUIRED");
        }
        // 완료 결과는 AI가 본 물건(label)이 약속 도구와 같을 때만 완료로 저장한다.
        // 이전에는 COMPLETED를 그대로 저장해 책 약속에 노트북 사진도 인증 완료됐다.
        Result result = request.result();
        String rejectionReason = request.rejectionReason();
        if (result == Result.COMPLETED) {
            if (request.detectedLabel() == null || request.detectedLabel().isBlank()) {
                throw error(HttpStatus.BAD_REQUEST, "DETECTED_LABEL_REQUIRED");
            }
            String promiseTool = jdbc.query("select tool_type from promise where id = ? and participant_id = ?",
                    rs -> rs.next() ? rs.getString("tool_type") : null, promiseId, participantId);
            if (promiseTool == null) {
                throw error(HttpStatus.CONFLICT, "ATTEMPT_NOT_CURRENT_OR_WITHDRAWN");
            }
            if (!PhotoToolMatchPolicy.matches(promiseTool, request.detectedLabel())) {
                result = Result.REJECTED;
                rejectionReason = PhotoToolMatchPolicy.TOOL_MISMATCH;
            }
        }
        String nextStatus = switch (result) {
            case COMPLETED -> "Completed";
            case REJECTED -> "Rejected";
            case TECH_ERROR -> "TechFailed";
        };
        Instant now = clock.instant();
        int updated = jdbc.update("""
                update auth_attempt a
                   set processing_status = ?, decision_result = ?, rejection_reason = ?,
                       technical_error_type = ?, model_version = ?, decided_at = ?
                 where a.id = ? and a.promise_id = ? and a.processing_status = 'Evaluating'
                   and exists (select 1 from promise p join participant part on part.id = p.participant_id
                                where p.id = a.promise_id and p.participant_id = ?
                                  and a.valid_received_at >= p.scheduled_at
                                  and a.valid_received_at < p.scheduled_at + interval '30 minutes'
                                  and part.lifecycle_status <> 'Withdrawn')
                """, nextStatus, result.name(), rejectionReason,
                request.technicalErrorType(), request.modelVersion(), Timestamp.from(now), attemptId, promiseId, participantId);
        if (updated != 1) {
            throw error(HttpStatus.CONFLICT, "ATTEMPT_NOT_CURRENT_OR_WITHDRAWN");
        }
        if (result == Result.COMPLETED) {
            jdbc.update("update promise set lifecycle_status = 'AuthCompleted', auth_completed_at = ?, "
                            + "final_outcome = null, updated_at = ? where id = ? and participant_id = ? "
                            + "and lifecycle_status <> 'Cancelled' and final_outcome is distinct from 'Withdrawn'",
                    Timestamp.from(now), Timestamp.from(now), promiseId, participantId);
            // 인증 완료도 실제 시작 자기보고 대상이다(R-36). 이전에는 AuthIncomplete만 설문이 생성됐다.
            com.dodu.survey.CompletedPromiseSurveyOpener.open(jdbc, promiseId);
        } else if (result == Result.TECH_ERROR) {
            jdbc.update("update promise set lifecycle_status = 'Closed', final_outcome = 'TechFailed', "
                            + "closed_at = ?, updated_at = ? where id = ? and participant_id = ? "
                            + "and lifecycle_status not in ('AuthCompleted', 'Cancelled') "
                            + "and final_outcome is distinct from 'Withdrawn'",
                    Timestamp.from(now), Timestamp.from(now), promiseId, participantId);
        }
        return new ResultResponse(attemptId, promiseId, nextStatus, result.name(), now);
    }

    private UUID participantFromBearer(String authorization) {
        return authService.authenticate(authorization).participant().id();
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
