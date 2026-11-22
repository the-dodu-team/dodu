package com.dodu.intervention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 즉시 판정 전용 실행기. 스레드·대기열 수를 제한해 AI가 느릴 때 서버 자원을 다 쓰지 않게 한다.
 * 넘친 작업은 버리지 않고(DB에 남음) 30초 주기 워커가 처리한다.
 */
@Configuration
@ConditionalOnProperty(name = "dodu.photo-evaluation.immediate.enabled", havingValue = "true", matchIfMissing = true)
public class PhotoEvaluationExecutorConfig {
    public static final String EXECUTOR = "photoEvaluationExecutor";

    @Bean(name = EXECUTOR)
    ThreadPoolTaskExecutor photoEvaluationExecutor(
            @Value("${dodu.photo-evaluation.immediate.threads:2}") int threads,
            @Value("${dodu.photo-evaluation.immediate.queue-capacity:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("photo-eval-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        // 종료 시 진행 중인 판정을 잠시 기다린다. 끝나지 못한 작업은 점유 만료 후 워커가 다시 처리한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        return executor;
    }
}
