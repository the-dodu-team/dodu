package com.dodu.notification;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class NotificationSubscriptionRepository {
    private final JdbcTemplate jdbc;
    public NotificationSubscriptionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String lockParticipantAndFindSessionHash(UUID participantId, UUID sessionId) {
        jdbc.queryForObject("select id from participant where id = ? for update", UUID.class, participantId);
        return jdbc.queryForObject("select session_token_hash from participant_session where id = ?", String.class, sessionId);
    }
    public void lockToken(String hash) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, hash);
    }
    public void deactivateOtherTargets(UUID participantId, String hash, Instant now) {
        jdbc.update("""
                update notification_subscription
                   set lifecycle_status = 'Inactive', deactivated_at = ?, push_token = null
                 where lifecycle_status = 'Active'
                   and ((participant_id = ? and subscription_key_hash <> ?)
                     or (participant_id <> ? and subscription_key_hash = ?))
                """, Timestamp.from(now), participantId, hash, participantId, hash);
    }
    public void activateExisting(UUID participantId, UUID sessionId, String hash, String token, Instant now) {
        jdbc.update("""
                update notification_subscription
                   set participant_id = ?, participant_session_id = ?, lifecycle_status = 'Active',
                       activated_at = ?, last_seen_at = ?, deactivated_at = null, push_token = ?
                 where subscription_key_hash = ?
                """, participantId, sessionId, Timestamp.from(now), Timestamp.from(now), token, hash);
    }
    public UUID findOwned(UUID participantId, String hash) {
        return jdbc.query("select id from notification_subscription where participant_id = ? and subscription_key_hash = ?",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null, participantId, hash);
    }
    public void insert(UUID participantId, UUID sessionId, String hash, String token, Instant now) {
        jdbc.update("""
                update notification_subscription set lifecycle_status = 'Inactive', deactivated_at = ?, push_token = null
                 where participant_id = ? and lifecycle_status = 'Active'
                """, Timestamp.from(now), participantId);
        jdbc.update("""
                insert into notification_subscription
                    (participant_id, participant_session_id, subscription_key_hash, push_token,
                     lifecycle_status, activated_at, last_seen_at)
                values (?, ?, ?, ?, 'Active', ?, ?)
                """, participantId, sessionId, hash, token, Timestamp.from(now), Timestamp.from(now));
    }
    public void deactivateSession(UUID participantId, UUID sessionId, Instant now) {
        jdbc.update("""
                update notification_subscription
                   set lifecycle_status = 'Inactive', deactivated_at = ?, last_seen_at = ?, push_token = null
                 where participant_id = ? and participant_session_id = ? and lifecycle_status = 'Active'
                """, Timestamp.from(now), Timestamp.from(now), participantId, sessionId);
    }
}
