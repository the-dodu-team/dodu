package com.dodu.survey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.dodu.notification.NotificationSchedulePolicy;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 인증 완료 약속에도 자기보고(실제 시작) 설문을 만든다.
 * 인증 완료는 실제 작업 시작의 증거가 아니므로 AuthIncomplete와 같은 안내 시각·24시간 규칙으로 따로 묻는다.
 * 안내 시각은 KST 정오 전 약속이면 당일 18시, 정오 이후면 다음 날 11시다.
 */
public final class CompletedPromiseSurveyOpener {
    private static final NotificationSchedulePolicy SCHEDULE = new NotificationSchedulePolicy();
    private static final ValidationSurveyPolicy POLICY = new ValidationSurveyPolicy();

    private CompletedPromiseSurveyOpener() { }

    /** 약속이 실제로 AuthCompleted이고 대체되지 않았을 때만 만든다. 이미 있으면 그대로 둔다. */
    public static void open(JdbcTemplate jdbc, UUID promiseId) {
        Instant scheduledAt = jdbc.query("""
                select scheduled_at from promise
                 where id = ? and lifecycle_status = 'AuthCompleted' and superseded_by_promise_id is null
                """, rs -> rs.next() ? rs.getTimestamp("scheduled_at").toInstant() : null, promiseId);
        if (scheduledAt == null) return;
        Instant availableAt = SCHEDULE.surveyReminderAt(scheduledAt);
        jdbc.update("""
                insert into validation_survey(promise_id, questionnaire_version, lifecycle_status,
                    eligibility_status, available_at, expires_at)
                values (?, 'v2.5.2', 'Pending', 'AuthCompleted', ?, ?)
                on conflict (promise_id) do nothing
                """, promiseId, Timestamp.from(availableAt), Timestamp.from(POLICY.expiresAt(availableAt)));
    }
}
