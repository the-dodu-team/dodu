package com.dodu.access;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/access")
public class ParticipantAccessController {
    private final JdbcTemplate jdbc;
    private final AccessCredentialHasher hasher;

    public ParticipantAccessController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.hasher = new AccessCredentialHasher(System.getenv().getOrDefault(
                "DODU_CREDENTIAL_PEPPER", "local-development-only"));
    }

    @PostMapping("/enter")
    @Transactional
    public AccessResponse enter(@RequestBody AccessRequest request) {
        if (request == null || blank(request.code()) || blank(request.deviceKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code and deviceKey are required");
        }

        ParticipantRow participant = findParticipant(request.code());
        if (participant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCESS_REQUIRED");
        }

        CredentialRow credential = jdbc.query("select secret_hash, revoked_at, expires_at from access_credential "
                        + "where participant_id = ? order by issued_at desc limit 1",
                rs -> rs.next() ? new CredentialRow(rs.getString("secret_hash"),
                        rs.getObject("revoked_at", Instant.class), rs.getObject("expires_at", Instant.class)) : null,
                participant.id());
        Instant now = Instant.now();
        if (credential == null || credential.revokedAt() != null ||
                (credential.expiresAt() != null && !now.isBefore(credential.expiresAt())) ||
                !hasher.matches(request.code(), credential.secretHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ACCESS_REQUIRED");
        }

        String deviceHash = hasher.hash(request.deviceKey());
        UUID deviceId = upsertDevice(participant.id(), deviceHash, now);
        jdbc.update("update participant_session set lifecycle_status = 'Revoked', revoked_at = ?, "
                        + "revoke_reason = 'SESSION_REPLACED' where participant_id = ? "
                        + "and lifecycle_status = 'Active'", now, participant.id());
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String tokenHash = hasher.hash(rawToken);
        jdbc.update("insert into participant_session(participant_id, participant_device_id, session_token_hash, "
                        + "lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, 'Active', ?, ?, ?)",
                participant.id(), deviceId, tokenHash, now, now, participant.accessEndsAt());
        return new AccessResponse(rawToken, participant.id().toString(), participant.accessEndsAt());
    }

    private ParticipantRow findParticipant(String code) {
        return jdbc.query("select id, access_ends_at, lifecycle_status from participant p "
                        + "join study_period s on s.id = p.study_period_id where p.public_code = ?",
                rs -> rs.next() ? new ParticipantRow(UUID.fromString(rs.getString("id")),
                        rs.getObject("access_ends_at", Instant.class), rs.getString("lifecycle_status")) : null, code);
    }

    private UUID upsertDevice(UUID participantId, String deviceHash, Instant now) {
        UUID existing = jdbc.query("select id from participant_device where participant_id = ? and device_key_hash = ?",
                rs -> rs.next() ? UUID.fromString(rs.getString("id")) : null, participantId, deviceHash);
        if (existing != null) {
            jdbc.update("update participant_device set lifecycle_status = 'Active', last_seen_at = ?, deactivated_at = null where id = ?",
                    now, existing);
            return existing;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) "
                        + "values (?, ?, ?, 'Active', ?, ?)", id, participantId, deviceHash, now, now);
        return id;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record AccessRequest(String code, String deviceKey) { }
    public record AccessResponse(String accessToken, String participantId, Instant accessEndsAt) { }
    private record ParticipantRow(UUID id, Instant accessEndsAt, String lifecycleStatus) { }
    private record CredentialRow(String secretHash, Instant revokedAt, Instant expiresAt) { }
}
