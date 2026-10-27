package com.dodu.survey;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.dodu.participant.service.ParticipantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "검증 설문")
@RequestMapping("/api/promises/{promiseId}/validation-survey")
public class ValidationSurveyController {
    private final JdbcTemplate jdbc;
    private final ValidationSurveyPolicy policy;
    private final Clock clock;
    private final ParticipantAuthService authService;

    public ValidationSurveyController(JdbcTemplate jdbc, Clock clock, ParticipantAuthService authService) {
        this.jdbc = jdbc;
        this.policy = new ValidationSurveyPolicy();
        this.clock = clock;
        this.authService = authService;
    }

    @Operation(summary = "검증 설문 조회")
    @GetMapping
    public SurveyResponse get(@PathVariable UUID promiseId,
                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID participantId = participantFor(authorization);
        return find(promiseId, participantId);
    }

    private SurveyResponse find(UUID promiseId, UUID participantId) {
        Instant now = clock.instant();
        jdbc.update("""
                update validation_survey
                   set lifecycle_status = 'Prompted', prompted_at = coalesce(prompted_at, ?)
                 where promise_id = ? and lifecycle_status = 'Pending'
                   and available_at is not null and expires_at is not null
                   and ? >= available_at and ? < expires_at
                """, now, promiseId, now, now);
        jdbc.update("""
                update validation_survey
                   set lifecycle_status = 'Expired'
                 where promise_id = ?
                   and lifecycle_status in ('Pending', 'Prompted', 'Reminded')
                   and expires_at is not null and expires_at <= ?
                """, promiseId, now);
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

    @Operation(summary = "검증 설문 건너뛰기")
    @PostMapping("/skip")
    public SurveyResponse skip(@PathVariable UUID promiseId,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID participantId = participantFor(authorization);
        Instant now = clock.instant();
        int updated = jdbc.update("""
                update validation_survey vs
                   set lifecycle_status = 'Skipped', skipped_at = ?
                 where vs.promise_id = ?
                   and exists (select 1 from promise p where p.id = vs.promise_id and p.participant_id = ?)
                   and vs.lifecycle_status in ('Pending', 'Prompted', 'Reminded')
                   and vs.available_at is not null and vs.expires_at is not null
                   and ? >= vs.available_at and ? < vs.expires_at
                """, now, promiseId, participantId, now, now);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "survey is not skippable");
        }
        return find(promiseId, participantId);
    }

    @Operation(summary = "검증 설문 제출")
    @PostMapping
    public SurveyResponse submit(@PathVariable UUID promiseId,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
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
        return authService.authenticate(authorization).participant().id();
    }

    public record SurveySubmission(Boolean actualStarted, String startOrder, String startDelayBucket, String noStartReason) {}
    public record SurveyResponse(UUID id, String questionnaireVersion, String lifecycleStatus, String eligibilityStatus,
                                 Boolean actualStarted, String startOrder, String startDelayBucket, String noStartReason,
                                 Instant availableAt, Instant expiresAt, Instant submittedAt) {}
}
