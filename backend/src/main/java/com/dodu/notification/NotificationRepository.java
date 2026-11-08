package com.dodu.notification;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<NotificationView> listSentInApp(UUID participantId) {
        return jdbc.query("""
                select id, promise_id, notification_type, scheduled_at, sent_at, read_at
                  from notification_delivery
                 where participant_id = ? and channel = 'IN_APP' and lifecycle_status = 'SENT'
                 order by coalesce(sent_at, scheduled_at) desc
                 limit 50
                """, (rs, row) -> view(rs), participantId);
    }
    public int markOwnedSentAsRead(UUID participantId, UUID notificationId, Instant now) {
        return jdbc.update("""
                update notification_delivery set read_at = coalesce(read_at, ?)
                 where id = ? and participant_id = ? and channel = 'IN_APP' and lifecycle_status = 'SENT'
                """, Timestamp.from(now), notificationId, participantId);
    }
    public NotificationView findOwned(UUID participantId, UUID notificationId) {
        return jdbc.query("""
                select id, promise_id, notification_type, scheduled_at, sent_at, read_at
                  from notification_delivery where id = ? and participant_id = ?
                """, rs -> rs.next() ? view(rs) : null, notificationId, participantId);
    }
    private static NotificationView view(ResultSet rs) throws SQLException {
        return new NotificationView(rs.getObject("id", UUID.class), rs.getObject("promise_id", UUID.class),
                rs.getString("notification_type"), rs.getTimestamp("scheduled_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());
    }
}
