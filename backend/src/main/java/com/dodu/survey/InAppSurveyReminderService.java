package com.dodu.survey;

import com.dodu.participant.domain.SessionContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InAppSurveyReminderService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InAppSurveyReminderService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean claim(UUID promiseId, SessionContext context) {
        var now = Timestamp.from(clock.instant());
        // Conditional UPDATE serializes competing tabs/nodes without changing survey eligibility.
        return jdbc.update("""
                update validation_survey vs set inapp_reprompted_at = ?
                 where vs.promise_id = ? and vs.inapp_reprompted_at is null
                   and vs.eligibility_status in ('AuthCompleted', 'AuthIncomplete')
                   and vs.lifecycle_status in ('Pending', 'Prompted', 'Reminded')
                   and vs.available_at <= ? and vs.expires_at > ?
                   and exists (select 1 from promise p join participant u on u.id = p.participant_id
                                join participant_session s on s.participant_id = u.id
                                join study_period sp on sp.id = u.study_period_id
                                where p.id = vs.promise_id and p.superseded_by_promise_id is null and u.id = ? and s.id = ?
                                  and u.withdrawn_at is null and u.access_disabled_at is null
                                  and s.lifecycle_status = 'Active' and s.revoked_at is null
                                  and s.expires_at > ? and (sp.access_ends_at is null or sp.access_ends_at > ?))
                """, now, promiseId, now, now, context.participant().id(), context.session().id(), now, now) == 1;
    }
}
