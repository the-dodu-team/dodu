package com.dodu.intervention;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

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

import com.dodu.access.AccessCredentialHasher;

@RestController
@RequestMapping("/api/promises/{promiseId}/photo-attempts/{attemptId}/result")
public class PhotoEvaluationResultController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AccessCredentialHasher hasher;

    @Autowired
    public PhotoEvaluationResultController(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    PhotoEvaluationResultController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.hasher = new AccessCredentialHasher(System.getenv().getOrDefault(
                "DODU_CREDENTIAL_PEPPER", "local-development-only"));
    }

    @PostMapping
    @Transactional
    public ResultResponse save(@PathVariable UUID promiseId, @PathVariable UUID attemptId,
                               @RequestHeader("Authorization") String authorization,
                               @RequestBody ResultRequest request) {
        UUID participantId = participantFromBearer(authorization);
        if (request == null || request.result() == null) {
            throw error(HttpStatus.BAD_REQUEST, "RESULT_REQUIRED");
        }
        String nextStatus = switch (request.result()) {
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
                                  and part.lifecycle_status <> 'Withdrawn')
                """, nextStatus, request.result().name(), request.rejectionReason(),
                request.technicalErrorType(), request.modelVersion(), Timestamp.from(now), attemptId, promiseId, participantId);
        if (updated != 1) {
            throw error(HttpStatus.CONFLICT, "ATTEMPT_NOT_CURRENT_OR_WITHDRAWN");
        }
        if (request.result() == Result.COMPLETED) {
            jdbc.update("update promise set lifecycle_status = 'AuthCompleted', auth_completed_at = ?, "
                            + "final_outcome = null, updated_at = ? where id = ? and participant_id = ? "
                            + "and lifecycle_status <> 'Cancelled' and final_outcome is distinct from 'Withdrawn'",
                    Timestamp.from(now), Timestamp.from(now), promiseId, participantId);
        } else if (request.result() == Result.TECH_ERROR) {
            jdbc.update("update promise set lifecycle_status = 'Closed', final_outcome = 'TechFailed', "
                            + "closed_at = ?, updated_at = ? where id = ? and participant_id = ? "
                            + "and lifecycle_status not in ('AuthCompleted', 'Cancelled') "
                            + "and final_outcome is distinct from 'Withdrawn'",
                    Timestamp.from(now), Timestamp.from(now), promiseId, participantId);
        }
        return new ResultResponse(attemptId, promiseId, nextStatus, request.result().name(), now);
    }

    private UUID participantFromBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw error(HttpStatus.UNAUTHORIZED, "ACCESS_REQUIRED");
        }
        String tokenHash = hasher.hash(authorization.substring(7).trim());
        UUID id = jdbc.query("select participant_id from participant_session where session_token_hash = ? "
                + "and lifecycle_status = 'Active' and expires_at > now()",
                rs -> rs.next() ? rs.getObject("participant_id", UUID.class) : null, tokenHash);
        if (id == null) throw error(HttpStatus.UNAUTHORIZED, "SESSION_REPLACED");
        return id;
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    public enum Result { COMPLETED, REJECTED, TECH_ERROR }
    public record ResultRequest(Result result, String rejectionReason, String technicalErrorType, String modelVersion) { }
    public record ResultResponse(UUID attemptId, UUID promiseId, String processingStatus, String decisionResult, Instant decidedAt) { }
}
