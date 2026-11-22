package com.dodu.intervention;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사진 제출 트랜잭션이 커밋된 뒤에만 판정을 바로 시작한다. 롤백된 제출은 이벤트가 전달되지 않는다.
 * 실행기가 가득 차거나 처리 중 실패해도 작업은 DB에 READY/PROCESSING으로 남아 30초 주기 워커가 처리한다.
 */
@Component
@ConditionalOnProperty(name = "dodu.photo-evaluation.immediate.enabled", havingValue = "true", matchIfMissing = true)
public class PhotoEvaluationImmediateDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PhotoEvaluationImmediateDispatcher.class);
    private final PhotoEvaluationRecoveryWorker worker;
    private final TaskExecutor executor;

    public PhotoEvaluationImmediateDispatcher(PhotoEvaluationRecoveryWorker worker,
            @Qualifier(PhotoEvaluationExecutorConfig.EXECUTOR) TaskExecutor executor) {
        this.worker = worker;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PhotoEvaluationRequested event) {
        try {
            executor.execute(() -> run(event));
        } catch (RejectedExecutionException rejected) {
            log.warn("DODU_PHOTO_EVAL stage=DEFERRED code=EXECUTOR_FULL attempt={}", event.attemptId());
        }
    }

    private void run(PhotoEvaluationRequested event) {
        try {
            worker.processNow(event.attemptId());
        } catch (RuntimeException failed) {
            // 예외 내용에 사진·접근정보가 섞이지 않도록 종류만 남긴다. 작업은 점유 만료 후 워커가 다시 잡는다.
            log.warn("DODU_PHOTO_EVAL stage=DEFERRED code=IMMEDIATE_FAILED type={} attempt={}",
                    failed.getClass().getSimpleName(), event.attemptId());
        }
    }
}
