package com.dodu.intervention;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사진 제출 트랜잭션이 커밋된 뒤에만 판정을 바로 시작한다. 롤백된 제출은 이벤트가 전달되지 않는다.
 * 실행기가 가득 차거나 처리 중 실패해도 작업은 DB에 READY/PROCESSING으로 남아 30초 주기 워커가 처리한다.
 *
 * <p>전용 실행기는 스레드·대기열 수를 제한해 AI가 느릴 때 서버 자원을 다 쓰지 않게 한다. 남는 작업은 버리지 않고
 * (DB에 남음) 30초 주기 워커가 처리한다.
 *
 * <p>이전: 실행기를 {@code PhotoEvaluationExecutorConfig}에서 {@code photoEvaluationExecutor} 빈으로 등록했다.
 * Executor 빈이 하나라도 있으면 Spring Boot가 기본 {@code applicationTaskExecutor}를 만들지 않아,
 * FCM이 켜진 운영에서 그 빈을 쓰는 {@code DeviceChangeNotificationListener}가 생성되지 못하고 서버가 시작하지 못했다.
 * 그래서 실행기를 빈으로 등록하지 않고 이 클래스가 직접 만들고 종료한다.
 */
@Component
@ConditionalOnProperty(name = "dodu.photo-evaluation.immediate.enabled", havingValue = "true", matchIfMissing = true)
public class PhotoEvaluationImmediateDispatcher implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(PhotoEvaluationImmediateDispatcher.class);
    private final PhotoEvaluationRecoveryWorker worker;
    private final TaskExecutor executor;
    private final ThreadPoolTaskExecutor ownedExecutor;
    /**
     * 대기열에 사진을 함께 실어 둘 최대 크기. 넘으면 사진 없이 넣어 처리 시 저장소에서 읽는다.
     * 대기열(기본 20)×이 값이 즉시 판정이 붙잡는 사진 메모리의 상한이다(기본 2MB → 최대 약 44MB).
     */
    static final int DEFAULT_INLINE_PHOTO_MAX_BYTES = 2 * 1024 * 1024;
    private final int inlinePhotoMaxBytes;

    @Autowired
    public PhotoEvaluationImmediateDispatcher(PhotoEvaluationRecoveryWorker worker,
            @Value("${dodu.photo-evaluation.immediate.threads:2}") int threads,
            @Value("${dodu.photo-evaluation.immediate.queue-capacity:20}") int queueCapacity,
            @Value("${dodu.photo-evaluation.immediate.inline-photo-max-bytes:" + DEFAULT_INLINE_PHOTO_MAX_BYTES + "}")
            int inlinePhotoMaxBytes) {
        this.worker = worker;
        this.ownedExecutor = newExecutor(threads, queueCapacity);
        this.executor = ownedExecutor;
        this.inlinePhotoMaxBytes = inlinePhotoMaxBytes;
    }

    /** 테스트용: 실행기를 직접 넘긴다(종료는 호출한 쪽 책임). */
    PhotoEvaluationImmediateDispatcher(PhotoEvaluationRecoveryWorker worker, TaskExecutor executor) {
        this(worker, executor, DEFAULT_INLINE_PHOTO_MAX_BYTES);
    }

    PhotoEvaluationImmediateDispatcher(PhotoEvaluationRecoveryWorker worker, TaskExecutor executor,
                                       int inlinePhotoMaxBytes) {
        this.worker = worker;
        this.ownedExecutor = null;
        this.executor = executor;
        this.inlinePhotoMaxBytes = inlinePhotoMaxBytes;
    }

    private static ThreadPoolTaskExecutor newExecutor(int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("photo-eval-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        // 종료 시 진행 중인 판정을 잠시 기다린다. 끝나지 못한 작업은 점유 만료 후 워커가 다시 처리한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        executor.initialize();
        return executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PhotoEvaluationRequested event) {
        PhotoObjectReader.PhotoObject photo = event.photo();
        PhotoObjectReader.PhotoObject inline = photo != null && photo.bytes().length <= inlinePhotoMaxBytes ? photo : null;
        try {
            executor.execute(() -> run(event.attemptId(), inline));
        } catch (RejectedExecutionException rejected) {
            log.warn("DODU_PHOTO_EVAL stage=DEFERRED code=EXECUTOR_FULL attempt={}", event.attemptId());
        }
    }

    // 이전: run(PhotoEvaluationRequested)로 이벤트를 통째로 넘겼다. 크기 상한을 넘은 사진을 대기열에 붙잡지 않도록 필요한 값만 넘긴다.
    private void run(java.util.UUID attemptId, PhotoObjectReader.PhotoObject inline) {
        try {
            worker.processNow(attemptId, inline);
        } catch (RuntimeException failed) {
            // 예외 내용에 사진·접근정보가 섞이지 않도록 종류만 남긴다. 작업은 점유 만료 후 워커가 다시 잡는다.
            log.warn("DODU_PHOTO_EVAL stage=DEFERRED code=IMMEDIATE_FAILED type={} attempt={}",
                    failed.getClass().getSimpleName(), attemptId);
        }
    }

    @Override
    public void destroy() {
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }
}
