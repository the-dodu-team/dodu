package com.dodu.promise;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.dodu.notification.NotificationSchedulePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromiseLifecycleService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PromiseLifecycleService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional
    public void reconcile(UUID participantId) {
        lockParticipant(participantId);
        var now = Timestamp.from(clock.instant());
        // Lock first, then inspect attempts in a new READ COMMITTED statement. Photo acceptance
        // takes the same promise lock, so a committed pre-deadline attempt cannot be overlooked.
        var candidates = jdbc.query("""
                select id, scheduled_at from promise
                 where participant_id = ? and lifecycle_status in ('Scheduled', 'AuthAvailable')
                   and auth_window_ends_at <= ? for update
                """, (rs, i) -> new Expired(rs.getObject("id", UUID.class), rs.getTimestamp("scheduled_at").toInstant()), participantId, now);
        for (var row : candidates) {
            int changed = jdbc.update("""
                    update promise p set lifecycle_status = 'AuthIncomplete', final_outcome = 'AuthIncomplete',
                           closed_at = ?, updated_at = ?, row_version = row_version + 1
                     where id = ? and lifecycle_status in ('Scheduled', 'AuthAvailable')
                       and auth_window_ends_at <= ?
                       and not exists (select 1 from auth_attempt a where a.promise_id = p.id
                                       and a.processing_status in ('Evaluating', 'Completed', 'TechFailed'))
                    """, now, now, row.id(), now);
            if (changed == 1) {
                Instant available = new NotificationSchedulePolicy().surveyReminderAt(row.scheduledAt());
                jdbc.update("""
                        insert into validation_survey(promise_id, questionnaire_version, lifecycle_status,
                            eligibility_status, available_at, expires_at)
                        values (?, 'v2.5.2', 'Pending', 'AuthIncomplete', ?, ?)
                        on conflict (promise_id) do nothing
                        """, row.id(), Timestamp.from(available), Timestamp.from(available.plus(Duration.ofHours(24))));
            }
        }
    }

    public void lockParticipant(UUID participantId) {
        jdbc.queryForObject("select id from participant where id = ? for update", UUID.class, participantId);
    }

    private record Expired(UUID id, Instant scheduledAt) { }
}
