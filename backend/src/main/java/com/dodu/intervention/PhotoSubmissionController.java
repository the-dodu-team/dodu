package com.dodu.intervention;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;

import com.dodu.participant.service.ParticipantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dodu.access.AccessCredentialHasher;

@RestController
@Tag(name = "사진 인증")
@RequestMapping("/api/promises/{promiseId}/photo-attempts")
public class PhotoSubmissionController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InterventionWindowPolicy windowPolicy;
    private final InterventionLifecycleGate lifecycleGate = new InterventionLifecycleGate();
    private final ParticipantAuthService authService;
    @Autowired
    public PhotoSubmissionController(JdbcTemplate jdbc, ParticipantAuthService authService) {
        this(jdbc, authService, Clock.systemUTC());
    }

    PhotoSubmissionController(JdbcTemplate jdbc, ParticipantAuthService authService, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.windowPolicy = new InterventionWindowPolicy(clock);
        this.authService = authService;
    }

    @Operation(summary = "사진 제출")
    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public PhotoAttemptResponse submit(
            @PathVariable UUID promiseId,
            @RequestHeader("Authorization") String authorization,
            @RequestParam UUID clientRequestId,
            @RequestParam String toolType,
            @RequestParam("photo") MultipartFile photo) {
        UUID participantId = participantFromBearer(authorization);
        if (photo == null || photo.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED");
        }
        if (!isSupportedPhotoType(photo.getContentType())) {
            throw error(HttpStatus.BAD_REQUEST, "PHOTO_TYPE_UNSUPPORTED");
        }
        if (toolType == null || toolType.isBlank() || toolType.length() > 32) {
            throw error(HttpStatus.BAD_REQUEST, "TOOL_TYPE_INVALID");
        }

        Instant now = clock.instant();
        PromiseRow promise = jdbc.query("""
                select p.scheduled_at, p.scheduled_date_kst, p.lifecycle_status, s.intervention_ends_at
                  from promise p
                  join participant participant on participant.id = p.participant_id
                  join study_period s on s.id = participant.study_period_id
                 where p.id = ? and p.participant_id = ?
                """, rs -> rs.next()
                        ? new PromiseRow(rs.getTimestamp("scheduled_at").toInstant(), rs.getObject("scheduled_date_kst", LocalDate.class),
                                rs.getString("lifecycle_status"),
                                rs.getTimestamp("intervention_ends_at") == null ? null : rs.getTimestamp("intervention_ends_at").toInstant())
                        : null, promiseId, participantId);
        if (promise == null) {
            throw error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        }
        if ("Closed".equals(promise.lifecycleStatus()) || "Cancelled".equals(promise.lifecycleStatus())) {
            throw error(HttpStatus.CONFLICT, "PROMISE_NOT_ACTIVE");
        }
        if (promise.interventionEndsAt() == null || lifecycleGate.decide(now, promise.interventionEndsAt(), false)
                != InterventionLifecycleGate.Decision.NEW_WORK_ALLOWED) {
            throw error(HttpStatus.CONFLICT, "INTERVENTION_CLOSED");
        }
        if (windowPolicy.validateNewPhotoSubmission(promise.scheduledAt()) !=
                InterventionWindowPolicy.PhotoSubmissionDecision.ALLOWED) {
            throw error(HttpStatus.CONFLICT, "PHOTO_WINDOW_CLOSED");
        }

        byte[] content;
        try {
            content = photo.getBytes();
        } catch (java.io.IOException e) {
            throw error(HttpStatus.BAD_REQUEST, "PHOTO_READ_FAILED");
        }
        String contentType = photo.getContentType() == null ? "application/octet-stream" : photo.getContentType();
        String fingerprint = PhotoSubmissionFingerprint.sha256(content, contentType, toolType);
        String photoHash = sha256(content);
        boolean usedOnAnotherDay = jdbc.queryForObject("""
                select exists(
                    select 1
                      from auth_attempt a
                      join photo_asset asset on asset.id = a.photo_asset_id
                      join promise prior_promise on prior_promise.id = a.promise_id
                     where prior_promise.participant_id = ?
                       and prior_promise.scheduled_date_kst <> ?
                       and asset.sha256_hash = ?
                )
                """, Boolean.class, participantId, promise.scheduledDateKst(), photoHash);
        if (usedOnAnotherDay) {
            throw error(HttpStatus.CONFLICT, "PHOTO_ALREADY_USED_ON_ANOTHER_DAY");
        }

        ExistingAttempt existing = jdbc.query("""
                select id, request_fingerprint, processing_status, request_received_at
                  from auth_attempt
                 where promise_id = ? and client_request_id = ?
                """, rs -> rs.next() ? new ExistingAttempt(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getString("processing_status"), rs.getTimestamp("request_received_at").toInstant()) : null,
                promiseId, clientRequestId);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw error(HttpStatus.CONFLICT, "PHOTO_REQUEST_KEY_REUSED");
            }
            ensureRecoveryJob(existing.id(), promiseId, now);
            return new PhotoAttemptResponse(existing.id(), promiseId, existing.processingStatus(),
                    existing.requestReceivedAt());
        }

        UUID assetId = UUID.randomUUID();
        jdbc.update("""
                insert into photo_asset(id, storage_key, content_type, byte_size, sha256_hash, received_at)
                values (?, ?, ?, ?, ?, ?)
                """, assetId, UUID.randomUUID(), contentType, content.length, photoHash, timestamp(now));
        int insertedCount = jdbc.update("""
                insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type,
                                         processing_status, request_received_at, valid_received_at, photo_asset_id)
                values (?, ?, ?, ?, ?, 'Evaluating', ?, ?, ?)
                on conflict (promise_id, client_request_id) do nothing
                """, UUID.randomUUID(), promiseId, clientRequestId, fingerprint, toolType, timestamp(now), timestamp(now), assetId);

        ExistingAttempt inserted = jdbc.query("""
                select id, request_fingerprint, processing_status, request_received_at
                  from auth_attempt
                 where promise_id = ? and client_request_id = ?
                """, rs -> rs.next() ? new ExistingAttempt(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getString("processing_status"), rs.getTimestamp("request_received_at").toInstant()) : null,
                promiseId, clientRequestId);
        if (inserted == null || !inserted.requestFingerprint().equals(fingerprint)) {
            throw error(HttpStatus.CONFLICT, "PHOTO_REQUEST_KEY_REUSED");
        }
        if (insertedCount == 0) {
            jdbc.update("delete from photo_asset where id = ?", assetId);
        }
        ensureRecoveryJob(inserted.id(), promiseId, now);
        return new PhotoAttemptResponse(inserted.id(), promiseId, inserted.processingStatus(), inserted.requestReceivedAt());
    }

    private void ensureRecoveryJob(UUID attemptId, UUID promiseId, Instant now) {
        jdbc.update("""
                insert into photo_evaluation_job(auth_attempt_id, promise_id, lifecycle_status, next_attempt_at)
                select id, promise_id, 'READY', ?
                  from auth_attempt
                 where id = ? and promise_id = ? and processing_status = 'Evaluating'
                on conflict (auth_attempt_id) do nothing
                """, timestamp(now), attemptId, promiseId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> concurrentEvaluation(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("PHOTO_EVALUATION_IN_PROGRESS"));
    }

    private UUID participantFromBearer(String authorization) {
        return authService.authenticate(authorization).participant().id();
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private static boolean isSupportedPhotoType(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/jpg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType);
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    public record PhotoAttemptResponse(UUID attemptId, UUID promiseId, String processingStatus, Instant receivedAt) { }
    public record ErrorResponse(String code) { }
    private record PromiseRow(Instant scheduledAt, LocalDate scheduledDateKst, String lifecycleStatus, Instant interventionEndsAt) { }
    private record ExistingAttempt(UUID id, String requestFingerprint, String processingStatus, Instant requestReceivedAt) { }
}
