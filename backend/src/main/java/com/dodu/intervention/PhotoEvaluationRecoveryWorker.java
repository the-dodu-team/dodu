package com.dodu.intervention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.dodu.intervention.domain.PhotoToolMatchPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims durable photo-evaluation jobs and applies only current, accepted attempts. */
@Service
public class PhotoEvaluationRecoveryWorker {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PhotoEvaluationRecoveryHandler handler;
    private final PhotoRecoveryBackoffPolicy backoff = new PhotoRecoveryBackoffPolicy();

    public PhotoEvaluationRecoveryWorker(JdbcTemplate jdbc, Clock clock,
                                         PhotoEvaluationRecoveryHandler handler) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.handler = handler;
    }

    @Scheduled(fixedDelayString = "${dodu.photo-recovery.worker-delay-ms:30000}")
    @Transactional
    public void runScheduled() {
        processDueJobs(clock.instant());
    }

    @Transactional
    public int processDueJobs(Instant now) {
        List<JobRow> jobs = jdbc.query("""
                select j.id, j.auth_attempt_id, j.promise_id, j.attempt_count,
                       a.storage_key, a.content_type
                  from photo_evaluation_job j
                  left join auth_attempt attempt on attempt.id = j.auth_attempt_id
                  left join photo_asset a on a.id = attempt.photo_asset_id
                 where (j.lifecycle_status = 'READY' and j.next_attempt_at <= ?)
                    or (j.lifecycle_status = 'PROCESSING' and j.lease_until <= ?)
                 order by j.next_attempt_at, j.created_at
                 for update of j skip locked
                 limit 10
                """, (rs, rowNum) -> new JobRow(rs.getObject("id", UUID.class),
                        rs.getObject("auth_attempt_id", UUID.class), rs.getObject("promise_id", UUID.class),
                        rs.getInt("attempt_count"), rs.getString("storage_key"),
                        rs.getString("content_type")), timestamp(now), timestamp(now));
        int processed = 0;
        for (JobRow job : jobs) {
            int attemptCount = job.attemptCount() + 1;
            jdbc.update("""
                    update photo_evaluation_job
                       set lifecycle_status = 'PROCESSING', attempt_count = ?,
                           lease_until = ?, updated_at = ?
                     where id = ?
                    """, attemptCount, timestamp(now.plusSeconds(60)), timestamp(now), job.jobId());
            if (!eligible(job, now)) {
                mark(job.jobId(), "SKIPPED", null, null, now);
                processed++;
                continue;
            }
            PhotoEvaluationRecoveryHandler.RecoveryResult result;
            try {
                result = handler.recover(new PhotoEvaluationRecoveryHandler.RecoveryJob(
                        job.jobId(), job.attemptId(), job.promiseId(), attemptCount,
                        job.storageKey(), job.contentType()));
            } catch (RuntimeException ignored) {
                result = PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("HANDLER_EXCEPTION");
            }
            apply(job, attemptCount, matchPromiseTool(job, result), now);
            processed++;
        }
        return processed;
    }

    /**
     * AI가 허용 물건을 봤더라도 약속 도구와 다르면 거절한다(예: 책 약속에 노트북 화면 사진).
     * 거절이므로 T+30 전이면 다시 찍어 제출할 수 있다. label이 없으면 판단할 수 없어 재시도한다.
     */
    private PhotoEvaluationRecoveryHandler.RecoveryResult matchPromiseTool(
            JobRow job, PhotoEvaluationRecoveryHandler.RecoveryResult result) {
        if (result.outcome() != PhotoEvaluationRecoveryHandler.Outcome.COMPLETED) return result;
        if (result.detectedLabel() == null) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_LABEL_MISSING");
        }
        String promiseTool = jdbc.queryForObject("select tool_type from promise where id = ?",
                String.class, job.promiseId());
        if (PhotoToolMatchPolicy.expectedLabel(promiseTool).isEmpty()) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.technicalFailure("PROMISE_TOOL_UNKNOWN");
        }
        if (!PhotoToolMatchPolicy.matches(promiseTool, result.detectedLabel())) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.rejected(
                    PhotoToolMatchPolicy.TOOL_MISMATCH, result.modelVersion());
        }
        return result;
    }

    private boolean eligible(JobRow job, Instant now) {
        return jdbc.query("""
                select a.processing_status, a.valid_received_at, p.scheduled_at,
                       part.lifecycle_status as participant_status,
                       p.lifecycle_status as promise_status
                  from auth_attempt a
                  join promise p on p.id = a.promise_id
                  join participant part on part.id = p.participant_id
                 where a.id = ? and a.promise_id = ?
                """, rs -> rs.next()
                        && "Evaluating".equals(rs.getString("processing_status"))
                        && !"Withdrawn".equals(rs.getString("participant_status"))
                        && rs.getTimestamp("valid_received_at") != null
                        && !rs.getTimestamp("valid_received_at").toInstant().isBefore(rs.getTimestamp("scheduled_at").toInstant())
                        && rs.getTimestamp("valid_received_at").toInstant().isBefore(
                                rs.getTimestamp("scheduled_at").toInstant().plus(InterventionWindowPolicy.AUTHENTICATION_WINDOW)),
                job.attemptId(), job.promiseId());
    }

    private void apply(JobRow job, int attemptCount,
                       PhotoEvaluationRecoveryHandler.RecoveryResult result, Instant now) {
        switch (result.outcome()) {
            case COMPLETED -> complete(job, result.modelVersion(), now);
            case REJECTED -> reject(job, result.errorCode(), result.modelVersion(), now);
            case TECHNICAL_FAILURE -> fail(job, result.errorCode(), now);
            case RETRYABLE_FAILURE -> {
                PhotoRecoveryBackoffPolicy.RetryPlan plan = backoff.afterFailure(attemptCount, now);
                if (plan.retry()) {
                    jdbc.update("""
                            update photo_evaluation_job
                               set lifecycle_status = 'READY', next_attempt_at = ?,
                                   lease_until = null, last_error_code = ?, updated_at = ?
                             where id = ?
                            """, timestamp(plan.nextAttemptAt()), result.errorCode(), timestamp(now), job.jobId());
                } else {
                    fail(job, result.errorCode(), now);
                }
            }
        }
    }

    private void complete(JobRow job, String modelVersion, Instant now) {
        // 이전: attempt 갱신 결과와 무관하게 약속 완료·설문 생성까지 진행했다.
        // AI 호출 중 결과 API가 attempt를 먼저 확정하면(0행) 그 결과를 덮어쓰지 않고 작업만 건너뛴다.
        int updated = jdbc.update("""
                update auth_attempt
                   set processing_status = 'Completed', decision_result = 'Completed',
                       model_version = ?, decided_at = ?
                 where id = ? and promise_id = ? and processing_status = 'Evaluating'
                """, modelVersion, timestamp(now), job.attemptId(), job.promiseId());
        if (updated != 1) {
            mark(job.jobId(), "SKIPPED", "ATTEMPT_NOT_CURRENT", null, now);
            return;
        }
        jdbc.update("""
                update promise set lifecycle_status = 'AuthCompleted', auth_completed_at = ?,
                       updated_at = ?
                 where id = ? and lifecycle_status <> 'Cancelled'
                   and final_outcome is distinct from 'Withdrawn'
                """, timestamp(now), timestamp(now), job.promiseId());
        // 복구 경로로 인증 완료된 약속도 자기보고 설문을 만든다(R-36).
        com.dodu.survey.CompletedPromiseSurveyOpener.open(jdbc, job.promiseId());
        mark(job.jobId(), "SUCCEEDED", null, null, now);
    }

    private void reject(JobRow job, String reason, String modelVersion, Instant now) {
        int updated = jdbc.update("""
                update auth_attempt
                   set processing_status = 'Rejected', decision_result = 'Rejected',
                       rejection_reason = ?, model_version = ?, decided_at = ?
                 where id = ? and promise_id = ? and processing_status = 'Evaluating'
                """, reason, modelVersion, timestamp(now), job.attemptId(), job.promiseId());
        // 이전: 0행이어도 SUCCEEDED로 기록했다. 이미 확정된 attempt면 건너뜀으로 남긴다.
        mark(job.jobId(), updated == 1 ? "SUCCEEDED" : "SKIPPED",
                updated == 1 ? null : "ATTEMPT_NOT_CURRENT", null, now);
    }

    private void fail(JobRow job, String errorCode, Instant now) {
        // 이전: attempt 갱신 결과와 무관하게 약속을 기술 실패로 종료했다.
        // 이미 거절 등으로 확정된 attempt(0행)라면 재촬영 기회를 막지 않도록 약속을 건드리지 않는다.
        int updated = jdbc.update("""
                update auth_attempt
                   set processing_status = 'TechFailed', decision_result = 'TechFailed',
                       technical_error_type = ?, decided_at = ?
                 where id = ? and promise_id = ? and processing_status = 'Evaluating'
                """, errorCode, timestamp(now), job.attemptId(), job.promiseId());
        if (updated != 1) {
            mark(job.jobId(), "SKIPPED", "ATTEMPT_NOT_CURRENT", null, now);
            return;
        }
        jdbc.update("""
                update promise set lifecycle_status = 'Closed', final_outcome = 'TechFailed',
                       closed_at = ?, updated_at = ?
                 where id = ? and lifecycle_status not in ('AuthCompleted', 'Cancelled')
                   and final_outcome is distinct from 'Withdrawn'
                """, timestamp(now), timestamp(now), job.promiseId());
        mark(job.jobId(), "EXHAUSTED", errorCode, null, now);
    }

    private void mark(UUID jobId, String status, String errorCode, Instant leaseUntil, Instant now) {
        jdbc.update("""
                update photo_evaluation_job
                   set lifecycle_status = ?, last_error_code = ?, lease_until = ?, updated_at = ?
                 where id = ?
                """, status, errorCode, leaseUntil == null ? null : timestamp(leaseUntil), timestamp(now), jobId);
    }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private record JobRow(UUID jobId, UUID attemptId, UUID promiseId, int attemptCount,
                          String storageKey, String contentType) { }
}
