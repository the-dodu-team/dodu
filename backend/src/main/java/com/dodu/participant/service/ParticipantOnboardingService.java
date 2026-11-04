package com.dodu.participant.service;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.SessionContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ParticipantOnboardingService {
    private final JdbcClient jdbc;
    private final Clock clock;

    public ParticipantOnboardingService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void complete(SessionContext context) {
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = jdbc.sql("""
                update participant p
                   set onboarding_completed_at = coalesce(onboarding_completed_at, :now),
                       updated_at = :now
                 where p.id = :participant
                   and p.withdrawn_at is null and p.access_disabled_at is null
                   and exists (select 1 from participant_session s
                                where s.id = :session and s.participant_id = p.id
                                  and s.lifecycle_status = 'Active'
                                  and s.revoked_at is null and s.expires_at > :now)
                   and exists (select 1 from study_period sp where sp.id = p.study_period_id
                                and (sp.access_ends_at is null or sp.access_ends_at > :now))
                """)
                .param("now", now).param("participant", context.participant().id())
                .param("session", context.session().id()).update();
        if (updated != 1) {
            throw new BusinessException(ErrorType.UNAUTHENTICATED, "ACCESS_REVOKED",
                    "접근 권한을 확인해 주세요.");
        }
    }
}
