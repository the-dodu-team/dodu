package com.dodu.promise;

import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.dodu.access.AccessCredentialHasher;

@RestController
@RequestMapping("/api/promises")
public class PromiseController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final PromiseRequestValidator validator = new PromiseRequestValidator();
    private final AccessCredentialHasher hasher = new AccessCredentialHasher(
            System.getenv().getOrDefault("DODU_CREDENTIAL_PEPPER", "local-development-only"));

    public PromiseController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    @Transactional
    public PromiseResponse create(@RequestHeader("Authorization") String authorization,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @RequestBody PromiseRequest request) {
        UUID participantId = participantFromBearer(authorization);
        String key = PromiseIdempotencyKeyPolicy.validate(idempotencyKey);
        String fingerprint = PromiseIdempotencyKeyPolicy.fingerprint(request);
        CreationRequest existing = findCreationRequest(participantId, key);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
            }
            if (existing.promiseId() == null) {
                throw error(HttpStatus.CONFLICT, "PROMISE_CREATION_IN_PROGRESS");
            }
            return findPromise(existing.promiseId(), participantId);
        }
        Instant now = Instant.now();
        StudyRow study = jdbc.query("select s.id, s.intervention_ends_at from study_period s "
                        + "join participant p on p.study_period_id = s.id where p.id = ?",
                rs -> rs.next() ? new StudyRow(UUID.fromString(rs.getString("id")),
                        rs.getTimestamp("intervention_ends_at").toInstant()) : null, participantId);
        if (study == null || study.interventionEndsAt() == null) throw error(HttpStatus.CONFLICT, "STUDY_PERIOD_ENDED");
        var valid = validator.validate(new PromiseRequestValidator.Request(request.taskTitle(), request.toolType(), request.scheduledAt()), now, study.interventionEndsAt());
        if (jdbc.queryForObject("select count(*) from promise where participant_id = ? and lifecycle_status in ('Scheduled','AuthAvailable','Evaluating')", Long.class, participantId) > 0)
            throw error(HttpStatus.CONFLICT, "PROMISE_ACTIVE_EXISTS");
        var date = valid.scheduledAt().atZone(KST).toLocalDate();
        if (jdbc.queryForObject("select count(*) from promise_date_usage where participant_id = ? and scheduled_date_kst = ?", Long.class, participantId, date) > 0)
            throw error(HttpStatus.CONFLICT, "PROMISE_DATE_ALREADY_USED");
        jdbc.update("insert into promise_creation_request(participant_id, idempotency_key, request_fingerprint) "
                        + "values (?, ?, ?) on conflict (participant_id, idempotency_key) do nothing",
                participantId, key, fingerprint);
        existing = findCreationRequest(participantId, key);
        if (existing == null) throw error(HttpStatus.CONFLICT, "PROMISE_CREATION_CONFLICT");
        if (!existing.requestFingerprint().equals(fingerprint)) throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        if (existing.promiseId() != null) return findPromise(existing.promiseId(), participantId);
        UUID policyId = jdbc.queryForObject("select id from policy_version where effective_from <= ? order by effective_from desc limit 1", UUID.class, timestamp(now));
        if (policyId == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "POLICY_UNAVAILABLE");
        UUID promiseId = UUID.randomUUID();
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, ?, ?, ?, ?, 'NORMAL', 'Scheduled', ?)",
                promiseId, participantId, policyId, valid.taskTitle(), timestamp(valid.scheduledAt()), date, valid.toolType().name(), timestamp(valid.scheduledAt().plusSeconds(1800)));
        jdbc.update("insert into promise_date_usage(participant_id, scheduled_date_kst, first_used_at) values (?, ?, ?)", participantId, date, timestamp(now));
        jdbc.update("update promise_creation_request set promise_id = ? where participant_id = ? and idempotency_key = ?",
                promiseId, participantId, key);
        return new PromiseResponse(promiseId, valid.taskTitle(), valid.toolType(), valid.scheduledAt(), 0);
    }

    private CreationRequest findCreationRequest(UUID participantId, String key) {
        return jdbc.query("select promise_id, request_fingerprint from promise_creation_request "
                        + "where participant_id = ? and idempotency_key = ?",
                rs -> rs.next() ? new CreationRequest(
                        rs.getObject("promise_id", UUID.class), rs.getString("request_fingerprint")) : null,
                participantId, key);
    }

    private PromiseResponse findPromise(UUID promiseId, UUID participantId) {
        return jdbc.query("select id, task_title, tool_type, scheduled_at, row_version from promise "
                        + "where id = ? and participant_id = ?",
                rs -> rs.next() ? new PromiseResponse(UUID.fromString(rs.getString("id")),
                        rs.getString("task_title"), PromiseRequestValidator.ToolType.valueOf(rs.getString("tool_type")),
                        rs.getTimestamp("scheduled_at").toInstant(), rs.getLong("row_version")) : null,
                promiseId, participantId);
    }

    @GetMapping("/active")
    public PromiseResponse active(@RequestHeader("Authorization") String authorization) {
        UUID participantId = participantFromBearer(authorization);
        return jdbc.query("select id, task_title, tool_type, scheduled_at, row_version from promise where participant_id = ? and lifecycle_status in ('Scheduled','AuthAvailable','Evaluating') order by scheduled_at limit 1",
                rs -> rs.next() ? new PromiseResponse(UUID.fromString(rs.getString("id")), rs.getString("task_title"), PromiseRequestValidator.ToolType.valueOf(rs.getString("tool_type")), rs.getTimestamp("scheduled_at").toInstant(), rs.getLong("row_version")) : null, participantId);
    }

    @PatchMapping("/{promiseId}/schedule")
    @Transactional
    public PromiseResponse reschedule(@RequestHeader("Authorization") String authorization,
                                      @PathVariable UUID promiseId, @RequestBody RescheduleRequest request) {
        UUID participantId = participantFromBearer(authorization);
        PromiseRow current = jdbc.query("select task_title, tool_type, scheduled_at, row_version, schedule_version, lifecycle_status "
                        + "from promise where id = ? and participant_id = ? for update",
                rs -> rs.next() ? new PromiseRow(rs.getString("task_title"), PromiseRequestValidator.ToolType.valueOf(rs.getString("tool_type")),
                        rs.getTimestamp("scheduled_at").toInstant(), rs.getLong("row_version"), rs.getInt("schedule_version"), rs.getString("lifecycle_status")) : null,
                promiseId, participantId);
        if (current == null) throw error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        Instant now = Instant.now();
        if (current.rowVersion() != request.expectedVersion()) throw error(HttpStatus.CONFLICT, "PROMISE_VERSION_CONFLICT");
        if (!current.scheduledAt().isAfter(now) || !current.lifecycleStatus().equals("Scheduled")) throw error(HttpStatus.CONFLICT, "PROMISE_NOT_MODIFIABLE");
        if (current.scheduleVersion() >= 2) throw error(HttpStatus.CONFLICT, "PROMISE_RESCHEDULE_LIMIT_REACHED");
        StudyRow study = jdbc.query("select intervention_ends_at from study_period s join participant p on p.study_period_id = s.id where p.id = ?",
                rs -> rs.next() ? new StudyRow(null, rs.getTimestamp("intervention_ends_at").toInstant()) : null, participantId);
        var valid = validator.validate(new PromiseRequestValidator.Request(current.taskTitle(), current.toolType(), request.scheduledAt()), now, study.interventionEndsAt());
        if (valid.scheduledAt().atZone(KST).toLocalDate().equals(current.scheduledAt().atZone(KST).toLocalDate()) &&
                jdbc.queryForObject("select count(*) from promise_date_usage where participant_id = ? and scheduled_date_kst = ?", Long.class, participantId, valid.scheduledAt().atZone(KST).toLocalDate()) > 0)
            throw error(HttpStatus.CONFLICT, "PROMISE_DATE_ALREADY_USED");
        jdbc.update("insert into promise_schedule_revision(promise_id, version, previous_scheduled_at, new_scheduled_at, previous_date_kst, new_date_kst, changed_at) values (?, ?, ?, ?, ?, ?, ?)",
                promiseId, current.scheduleVersion(), timestamp(current.scheduledAt()), timestamp(valid.scheduledAt()), current.scheduledAt().atZone(KST).toLocalDate(), valid.scheduledAt().atZone(KST).toLocalDate(), timestamp(now));
        jdbc.update("update promise set scheduled_at = ?, scheduled_date_kst = ?, auth_window_ends_at = ?, schedule_version = schedule_version + 1, row_version = row_version + 1, updated_at = ? where id = ? and row_version = ?",
                timestamp(valid.scheduledAt()), valid.scheduledAt().atZone(KST).toLocalDate(), timestamp(valid.scheduledAt().plusSeconds(1800)), timestamp(now), promiseId, request.expectedVersion());
        return new PromiseResponse(promiseId, current.taskTitle(), current.toolType(), valid.scheduledAt(), request.expectedVersion() + 1);
    }

    @PostMapping("/{promiseId}/cancel")
    @Transactional
    public PromiseResponse cancel(@RequestHeader("Authorization") String authorization,
                                  @PathVariable UUID promiseId, @RequestBody VersionRequest request) {
        UUID participantId = participantFromBearer(authorization);
        PromiseRow current = jdbc.query("select task_title, tool_type, scheduled_at, row_version, schedule_version, lifecycle_status from promise where id = ? and participant_id = ? for update",
                rs -> rs.next() ? new PromiseRow(rs.getString("task_title"), PromiseRequestValidator.ToolType.valueOf(rs.getString("tool_type")), rs.getTimestamp("scheduled_at").toInstant(), rs.getLong("row_version"), rs.getInt("schedule_version"), rs.getString("lifecycle_status")) : null,
                promiseId, participantId);
        if (current == null) throw error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        if (current.rowVersion() != request.expectedVersion()) throw error(HttpStatus.CONFLICT, "PROMISE_VERSION_CONFLICT");
        if (!current.scheduledAt().isAfter(Instant.now()) || !current.lifecycleStatus().equals("Scheduled")) throw error(HttpStatus.CONFLICT, "PROMISE_NOT_MODIFIABLE");
        jdbc.update("update promise set lifecycle_status = 'Closed', final_outcome = 'Cancelled', closed_at = now(), updated_at = now(), row_version = row_version + 1 where id = ? and row_version = ?",
                promiseId, request.expectedVersion());
        return new PromiseResponse(promiseId, current.taskTitle(), current.toolType(), current.scheduledAt(), request.expectedVersion() + 1);
    }

    private UUID participantFromBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) throw error(HttpStatus.UNAUTHORIZED, "ACCESS_REQUIRED");
        String tokenHash = hasher.hash(authorization.substring(7).trim());
        UUID id = jdbc.query("select participant_id from participant_session where session_token_hash = ? and lifecycle_status = 'Active' and expires_at > now()",
                rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null, tokenHash);
        if (id == null) throw error(HttpStatus.UNAUTHORIZED, "SESSION_REPLACED");
        return id;
    }

    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    public record PromiseRequest(String taskTitle, PromiseRequestValidator.ToolType toolType, String scheduledAt) { }
    public record RescheduleRequest(String scheduledAt, long expectedVersion) { }
    public record VersionRequest(long expectedVersion) { }
    public record PromiseResponse(UUID promiseId, String taskTitle, PromiseRequestValidator.ToolType toolType, Instant scheduledAt, long version) { }
    private record StudyRow(UUID id, Instant interventionEndsAt) { }
    private record PromiseRow(String taskTitle, PromiseRequestValidator.ToolType toolType, Instant scheduledAt, long rowVersion, int scheduleVersion, String lifecycleStatus) { }
    private record CreationRequest(UUID promiseId, String requestFingerprint) { }
}
