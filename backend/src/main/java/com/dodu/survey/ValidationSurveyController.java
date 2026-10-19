package com.dodu.survey;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/promises/{promiseId}/validation-survey")
public final class ValidationSurveyController {
    private final JdbcTemplate jdbc;
    private final ValidationSurveyPolicy policy;
    private final Clock clock;

    public ValidationSurveyController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.policy = new ValidationSurveyPolicy();
        this.clock = clock;
    }

    @GetMapping
    public SurveyResponse get(@PathVariable UUID promiseId, @RequestHeader("Authorization") String authorization) {
        UUID participantId = participantFor(authorization);
        return find(promiseId, participantId);
    }

    private SurveyResponse find(UUID promiseId, UUID participantId) {
        return jdbc.query("""
                select vs.id, vs.questionnaire_version, vs.lifecycle_status, vs.eligibility_status,
                       vs.actual_started, vs.start_order, vs.start_delay_bucket, vs.no_start_reason,
                       vs.available_at, vs.expires_at, vs.submitted_at
                  from validation_survey vs
                  join promise p on p.id = vs.promise_id
                 where vs.promise_id = ? and p.participant_id = ?
                """, rs -> {
                    if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                    return new SurveyResponse(
                    rs.getObject("id", UUID.class), rs.getString("questionnaire_version"),
                    rs.getString("lifecycle_status"), rs.getString("eligibility_status"),
                    (Boolean) rs.getObject("actual_started"), rs.getString("start_order"),
                    rs.getString("start_delay_bucket"), rs.getString("no_start_reason"),
                    rs.getObject("available_at", Instant.class), rs.getObject("expires_at", Instant.class),
                    rs.getObject("submitted_at", Instant.class));
                },
                promiseId, participantId);
    }

    @PostMapping
    public SurveyResponse submit(@PathVariable UUID promiseId, @RequestHeader("Authorization") String authorization,
                                 @RequestBody SurveySubmission request) {
        UUID participantId = participantFor(authorization);
        Instant now = clock.instant();
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<SurveyResponse>) connection -> {
            try (var statement = connection.prepareStatement("""
                    select vs.id, vs.questionnaire_version, vs.lifecycle_status, vs.eligibility_status,
                           vs.available_at, vs.expires_at
                      from validation_survey vs join promise p on p.id = vs.promise_id
                     where vs.promise_id = ? and p.participant_id = ? for update
                    """)) {
                statement.setObject(1, promiseId);
                statement.setObject(2, participantId);
                try (var rs = statement.executeQuery()) {
                    if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                    var eligibility = policy.eligibility(rs.getString("eligibility_status"));
                    Instant availableAt = rs.getObject("available_at", Instant.class);
                    Instant expiresAt = rs.getObject("expires_at", Instant.class);
                    if (!policy.canRespond(eligibility, availableAt, expiresAt, now)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "survey is not available");
                    }
                    if (request == null || request.actualStarted() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actualStarted is required");
                    }
                    try (var update = connection.prepareStatement("""
                            update validation_survey
                               set lifecycle_status = 'Submitted', actual_started = ?, start_order = ?,
                                   start_delay_bucket = ?, no_start_reason = ?, submitted_at = ?
                             where id = ? and lifecycle_status <> 'Submitted'
                            """)) {
                        update.setBoolean(1, request.actualStarted());
                        update.setString(2, request.startOrder());
                        update.setString(3, request.startDelayBucket());
                        update.setString(4, request.noStartReason());
                        update.setObject(5, now);
                        update.setObject(6, rs.getObject("id", UUID.class));
                        if (update.executeUpdate() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "survey already submitted");
                    }
                }
            }
            return find(promiseId, participantId);
        });
    }

    private UUID participantFor(String authorization) {
        String token = token(authorization);
        try {
            return jdbc.queryForObject("""
                    select participant_id from participant_session
                     where session_token_hash = encode(digest(?, 'sha256'), 'hex')
                       and lifecycle_status = 'Active' and expires_at > now()
                    """, UUID.class, token);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session");
        }
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token required");
        }
        return authorization.substring(7).trim();
    }

    public record SurveySubmission(Boolean actualStarted, String startOrder, String startDelayBucket, String noStartReason) {}
    public record SurveyResponse(UUID id, String questionnaireVersion, String lifecycleStatus, String eligibilityStatus,
                                 Boolean actualStarted, String startOrder, String startDelayBucket, String noStartReason,
                                 Instant availableAt, Instant expiresAt, Instant submittedAt) {}
}
