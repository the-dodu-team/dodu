package com.dodu.intervention;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.dodu.intervention.domain.PhotoToolMatchPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims durable photo-evaluation jobs and applies only current, accepted attempts.
 *
 * <p>처리 단계: ① 작업 차지(READY→PROCESSING, 단일 UPDATE) ② AI 호출(트랜잭션 밖)
 * ③ 결과 저장(짧은 트랜잭션, 차지한 회차가 그대로일 때만).
 * 사진 제출 커밋 직후 경로({@link #processNow})와 30초 주기 워커({@link #processDueJobs})가 같은 단계를 쓴다.
 */
@Service
public class PhotoEvaluationRecoveryWorker {
    static final int BATCH_LIMIT = 10;
    static final Duration LEASE = Duration.ofSeconds(60);
    private static final String WITHDRAWN = "Withdrawn";

    private static final RowMapper<ClaimedJob> CLAIMED = (rs, rowNum) -> new ClaimedJob(
            rs.getObject("id", UUID.class), rs.getObject("auth_attempt_id", UUID.class),
            rs.getObject("promise_id", UUID.class), rs.getInt("attempt_count"));

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PhotoEvaluationRecoveryHandler handler;
    private final TransactionTemplate tx;
    private final PhotoRecoveryBackoffPolicy backoff = new PhotoRecoveryBackoffPolicy();

    // 이전: (JdbcTemplate, Clock, PhotoEvaluationRecoveryHandler). 단계별 짧은 트랜잭션을 위해 트랜잭션 관리자를 받는다.
    public PhotoEvaluationRecoveryWorker(JdbcTemplate jdbc, Clock clock,
                                         PhotoEvaluationRecoveryHandler handler,
                                         PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.handler = handler;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // 이전: @Transactional 하나 안에서 최대 10건을 고르고 AI 호출까지 순서대로 처리했다.
    // 그래서 AI 응답을 기다리는 동안 DB 연결·행 잠금을 잡고 있었고, 뒤 작업은 앞 작업의 AI 시간만큼 늦게 시작했다.
    // 이제 사진 제출 직후 경로가 먼저 처리하고, 이 주기 실행은 놓친 작업·재시도·점유 만료 작업을 받는 안전망이다.
    @Scheduled(fixedDelayString = "${dodu.photo-recovery.worker-delay-ms:30000}")
    public void runScheduled() {
        processDueJobs(clock.instant());
    }

    /**
     * 기한이 된 작업을 한 건씩 차지해 처리한다(최대 {@value #BATCH_LIMIT}건).
     *
     * <p>{@code batchStart}는 배치 시작 시각이다. 작업마다 그 뒤로 흐른 시간을 더한 시각으로 차지·점유·판정 시각을 정한다.
     * 이전에는 배치 시작 시각 하나를 모든 작업에 썼다. 앞 작업의 AI가 오래 걸리면 뒤 작업은 이미 만료된 점유로 차지되어,
     * AI를 기다리는 동안 다른 쪽이 다시 잡아 AI를 중복 호출할 수 있었다.
     */
    public int processDueJobs(Instant batchStart) {
        Instant startedAt = clock.instant();
        int processed = 0;
        while (processed < BATCH_LIMIT) {
            Instant now = batchStart.plus(Duration.between(startedAt, clock.instant()));
            ClaimedJob claimed = claimNextDue(now);
            if (claimed == null) break;
            process(claimed, now, null);
            processed++;
        }
        return processed;
    }

    /**
     * 사진 제출 커밋 직후 호출한다. 해당 attempt의 작업이 지금 READY일 때만 차지해 처리한다.
     * 이미 다른 쪽이 차지했거나 재시도 대기(next_attempt_at 미래)면 아무것도 하지 않고 false를 돌려준다.
     */
    public boolean processNow(UUID attemptId) {
        return processNow(attemptId, null);
    }

    /** {@code preloaded}: 같은 제출 요청에서 받은 사진. 있으면 AI 호출 전 저장소 재조회를 건너뛴다. */
    public boolean processNow(UUID attemptId, PhotoObjectReader.PhotoObject preloaded) {
        Instant now = clock.instant();
        ClaimedJob claimed = claimReady(attemptId, now);
        if (claimed == null) return false;
        process(claimed, now, preloaded);
        return true;
    }

    private ClaimedJob claimNextDue(Instant now) {
        // 고르기와 PROCESSING 전환을 한 문장으로 처리한다. skip locked라 다른 쪽이 잡은 행은 건너뛴다.
        List<ClaimedJob> rows = jdbc.query("""
                with next_job as (
                    select id
                      from photo_evaluation_job
                     where (lifecycle_status = 'READY' and next_attempt_at <= ?)
                        or (lifecycle_status = 'PROCESSING' and lease_until <= ?)
                     order by next_attempt_at, created_at
                     for update skip locked
                     limit 1
                )
                update photo_evaluation_job j
                   set lifecycle_status = 'PROCESSING', attempt_count = j.attempt_count + 1,
                       lease_until = ?, updated_at = ?
                  from next_job
                 where j.id = next_job.id
                returning j.id, j.auth_attempt_id, j.promise_id, j.attempt_count
                """, CLAIMED, timestamp(now), timestamp(now), timestamp(now.plus(LEASE)), timestamp(now));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ClaimedJob claimReady(UUID attemptId, Instant now) {
        // 조건부 UPDATE라 워커와 동시에 와도 한쪽만 1행을 얻는다(다른 쪽은 행 잠금 해제 후 조건 재확인 → 0행).
        List<ClaimedJob> rows = jdbc.query("""
                update photo_evaluation_job
                   set lifecycle_status = 'PROCESSING', attempt_count = attempt_count + 1,
                       lease_until = ?, updated_at = ?
                 where auth_attempt_id = ? and lifecycle_status = 'READY' and next_attempt_at <= ?
                returning id, auth_attempt_id, promise_id, attempt_count
                """, CLAIMED, timestamp(now.plus(LEASE)), timestamp(now), attemptId, timestamp(now));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void process(ClaimedJob claimed, Instant now, PhotoObjectReader.PhotoObject preloaded) {
        JobRow job = load(claimed);
        if (!eligible(job, now)) {
            inOwnedJob(job, () -> mark(job.jobId(), "SKIPPED", null, null, now));
            return;
        }
        // AI 호출(사진 읽기 포함)은 트랜잭션 밖에서 한다.
        PhotoEvaluationRecoveryHandler.RecoveryResult result;
        try {
            result = handler.recover(new PhotoEvaluationRecoveryHandler.RecoveryJob(
                    job.jobId(), job.attemptId(), job.promiseId(), job.attemptCount(),
                    job.storageKey(), job.contentType()), preloaded);
        } catch (RuntimeException ignored) {
            result = PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("HANDLER_EXCEPTION");
        }
        PhotoEvaluationRecoveryHandler.RecoveryResult finalResult = result;
        inOwnedJob(job, () -> apply(job, job.attemptCount(), matchPromiseTool(job, finalResult), now));
    }

    /**
     * 결과 저장은 내가 차지한 그 회차(attempt_count)가 아직 PROCESSING일 때만 한다.
     * AI가 점유 시간(60초)보다 오래 걸려 다른 쪽이 다시 차지했다면 늦은 결과를 버린다.
     *
     * <p>AI를 기다리는 사이 참가자가 철회했다면 결과를 attempt에 쓰지 않고 작업을 건너뜀으로 끝낸다.
     * 참가자 행을 공유 잠금으로 읽으므로 철회(참가자 행 UPDATE)와 동시에 와도 둘 중 하나가 먼저 끝난 뒤 판단한다.
     */
    private void inOwnedJob(JobRow job, Runnable action) {
        tx.executeWithoutResult(status -> {
            Boolean owned = jdbc.query("""
                    select 1 from photo_evaluation_job
                     where id = ? and lifecycle_status = 'PROCESSING' and attempt_count = ?
                     for update
                    """, (ResultSetExtractor<Boolean>) ResultSet::next, job.jobId(), job.attemptCount());
            if (!Boolean.TRUE.equals(owned)) return;
            String participantStatus = jdbc.query("""
                    select part.lifecycle_status
                      from promise p
                      join participant part on part.id = p.participant_id
                     where p.id = ?
                     for share of part
                    """, (ResultSetExtractor<String>) rs -> rs.next() ? rs.getString(1) : null, job.promiseId());
            if (WITHDRAWN.equals(participantStatus)) {
                mark(job.jobId(), "SKIPPED", "PARTICIPANT_WITHDRAWN", null, clock.instant());
                return;
            }
            action.run();
        });
    }

    private JobRow load(ClaimedJob claimed) {
        return jdbc.query("""
                select a.storage_key, a.content_type
                  from auth_attempt attempt
                  left join photo_asset a on a.id = attempt.photo_asset_id
                 where attempt.id = ?
                """, rs -> rs.next()
                        ? new JobRow(claimed.jobId(), claimed.attemptId(), claimed.promiseId(), claimed.attemptCount(),
                                rs.getString("storage_key"), rs.getString("content_type"))
                        : new JobRow(claimed.jobId(), claimed.attemptId(), claimed.promiseId(), claimed.attemptCount(),
                                null, null), claimed.attemptId());
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
            return PhotoEvaluationRecoveryHandler.RecoveryResult.technicalFailure(PhotoToolMatchPolicy.PROMISE_TOOL_UNKNOWN);
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

    private record ClaimedJob(UUID jobId, UUID attemptId, UUID promiseId, int attemptCount) { }

    /** attemptCount: 이번에 차지하며 올린 회차(재시도 정책·소유 확인에 사용). */
    private record JobRow(UUID jobId, UUID attemptId, UUID promiseId, int attemptCount,
                          String storageKey, String contentType) { }
}
