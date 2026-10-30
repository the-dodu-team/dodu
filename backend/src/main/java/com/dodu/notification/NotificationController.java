package com.dodu.notification;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/participant/notifications")
public class NotificationController {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(SessionContext context) {
        List<NotificationResponse> notifications = jdbc.query("""
                select id, promise_id, notification_type, scheduled_at, sent_at, read_at
                  from notification_delivery
                 where participant_id = ? and channel = 'IN_APP' and lifecycle_status = 'SENT'
                 order by coalesce(sent_at, scheduled_at) desc
                 limit 50
                """, (rs, rowNum) -> new NotificationResponse(
                        rs.getObject("id", UUID.class), rs.getObject("promise_id", UUID.class),
                        rs.getString("notification_type"), rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                context.participant().id());
        return ApiResponse.success(notifications, ApiTime.now(clock));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId,
                                                       SessionContext context) {
        Instant now = clock.instant();
        int updated = jdbc.update("""
                update notification_delivery
                   set read_at = coalesce(read_at, ?)
                 where id = ? and participant_id = ? and channel = 'IN_APP' and lifecycle_status = 'SENT'
                """, java.sql.Timestamp.from(now), notificationId, context.participant().id());
        if (updated != 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        }
        NotificationResponse notification = jdbc.query("""
                select id, promise_id, notification_type, scheduled_at, sent_at, read_at
                  from notification_delivery where id = ? and participant_id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new NotificationResponse(
                    rs.getObject("id", UUID.class), rs.getObject("promise_id", UUID.class),
                    rs.getString("notification_type"), rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                    rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());
        },
                notificationId, context.participant().id());
        return ApiResponse.success(notification, ApiTime.now(clock));
    }

    public record NotificationResponse(UUID notificationId, UUID promiseId, String notificationType,
                                       Instant scheduledAt, Instant sentAt, Instant readAt) { }
}
