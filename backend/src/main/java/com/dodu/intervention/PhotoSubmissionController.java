package com.dodu.intervention;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
@RequestMapping("/api/promises/{promiseId}/photo-attempts")
public final class PhotoSubmissionController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InterventionWindowPolicy windowPolicy;
    private final AccessCredentialHasher hasher;

    public PhotoSubmissionController(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    PhotoSubmissionController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.windowPolicy = new InterventionWindowPolicy(clock);
        this.hasher = new AccessCredentialHasher(System.getenv().getOrDefault(
                "DODU_CREDENTIAL_PEPPER", "local-development-only"));
    }

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
        if (toolType == null || toolType.isBlank() || toolType.length() > 32) {
            throw error(HttpStatus.BAD_REQUEST, "TOOL_TYPE_INVALID");
        }

        Instant now = clock.instant();
        PromiseRow promise = jdbc.query("""
                select p.scheduled_at, p.lifecycle_status
                  from promise p
                 where p.id = ? and p.participant_id = ?
                """, rs -> rs.next()
                        ? new PromiseRow(rs.getObject("scheduled_at", Instant.class), rs.getString("lifecycle_status"))
                        : null, promiseId, participantId);
        if (promise == null) {
            throw error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        }
        if ("Closed".equals(promise.lifecycleStatus()) || "Cancelled".equals(promise.lifecycleStatus())) {
            throw error(HttpStatus.CONFLICT, "PROMISE_NOT_ACTIVE");
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

        ExistingAttempt existing = jdbc.query("""
                select id, request_fingerprint, processing_status, request_received_at
                  from auth_attempt
                 where promise_id = ? and client_request_id = ?
                """, rs -> rs.next() ? new ExistingAttempt(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getString("processing_status"), rs.getObject("request_received_at", Instant.class)) : null,
                promiseId, clientRequestId);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw error(HttpStatus.CONFLICT, "PHOTO_REQUEST_KEY_REUSED");
            }
            return new PhotoAttemptResponse(existing.id(), promiseId, existing.processingStatus(),
                    existing.requestReceivedAt());
        }

        UUID assetId = UUID.randomUUID();
        jdbc.update("""
                insert into photo_asset(id, storage_key, content_type, byte_size, sha256_hash, received_at)
                values (?, ?, ?, ?, ?, ?)
                """, assetId, UUID.randomUUID(), contentType, content.length, sha256(content), now);
        int insertedCount = jdbc.update("""
                insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type,
                                         processing_status, request_received_at, valid_received_at, photo_asset_id)
                values (?, ?, ?, ?, ?, 'Evaluating', ?, ?, ?)
                on conflict (promise_id, client_request_id) do nothing
                """, UUID.randomUUID(), promiseId, clientRequestId, fingerprint, toolType, now, now, assetId);

        ExistingAttempt inserted = jdbc.query("""
                select id, request_fingerprint, processing_status, request_received_at
                  from auth_attempt
                 where promise_id = ? and client_request_id = ?
                """, rs -> rs.next() ? new ExistingAttempt(
                        rs.getObject("id", UUID.class), rs.getString("request_fingerprint"),
                        rs.getString("processing_status"), rs.getObject("request_received_at", Instant.class)) : null,
                promiseId, clientRequestId);
        if (inserted == null || !inserted.requestFingerprint().equals(fingerprint)) {
            throw error(HttpStatus.CONFLICT, "PHOTO_REQUEST_KEY_REUSED");
        }
        if (insertedCount == 0) {
            jdbc.update("delete from photo_asset where id = ?", assetId);
        }
        return new PhotoAttemptResponse(inserted.id(), promiseId, inserted.processingStatus(), inserted.requestReceivedAt());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> concurrentEvaluation(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("PHOTO_EVALUATION_IN_PROGRESS"));
    }

    private UUID participantFromBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw error(HttpStatus.UNAUTHORIZED, "ACCESS_REQUIRED");
        }
        String tokenHash = hasher.hash(authorization.substring(7).trim());
        UUID id = jdbc.query("""
                select participant_id from participant_session
                 where session_token_hash = ? and lifecycle_status = 'Active' and expires_at > now()
                """, rs -> rs.next() ? rs.getObject("participant_id", UUID.class) : null, tokenHash);
        if (id == null) throw error(HttpStatus.UNAUTHORIZED, "SESSION_REPLACED");
        return id;
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
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
    private record PromiseRow(Instant scheduledAt, String lifecycleStatus) { }
    private record ExistingAttempt(UUID id, String requestFingerprint, String processingStatus, Instant requestReceivedAt) { }
}
