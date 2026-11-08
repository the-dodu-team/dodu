package com.dodu.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 안내 실패는 커밋된 새 기기 입장을 취소하지 않는다. 승인 요청으로 취급하지 않는다. */
@Component
@ConditionalOnProperty(name = "dodu.push.fcm.enabled", havingValue = "true")
public class DeviceChangeNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceChangeNotificationListener.class);
    private final DeviceChangePushSender sender;
    private final TaskExecutor executor;
    private final boolean dryRun;
    public DeviceChangeNotificationListener(DeviceChangePushSender sender,
            @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Value("${dodu.push.fcm.dry-run:false}") boolean dryRun) {
        this.sender = sender; this.executor = executor; this.dryRun = dryRun;
    }
    @TransactionalEventListener
    public void afterCommit(DeviceChangeNotice notice) {
        log.info("DODU_DEVICE_PUSH stage=COMMITTED trace={} targets={} dryRun={}",
                notice.traceId(), notice.tokens().size(), dryRun);
        if (notice.tokens().isEmpty()) {
            log.info("DODU_DEVICE_PUSH stage=SKIPPED trace={} code=NO_ACTIVE_SUBSCRIPTION", notice.traceId());
            return;
        }
        try { executor.execute(() -> deliver(notice)); }
        catch (RuntimeException ignored) {
            log.warn("DODU_DEVICE_PUSH stage=FAILED trace={} code=EXECUTOR_REJECTED", notice.traceId());
        }
    }
    private void deliver(DeviceChangeNotice notice) {
        for (String token : notice.tokens()) {
            log.info("DODU_DEVICE_PUSH stage=ATTEMPT trace={}", notice.traceId());
            try {
                DeviceChangePushSender.Result result = sender.send(token);
                switch (result.status()) {
                    case ACCEPTED -> log.info("DODU_DEVICE_PUSH stage=FCM_ACCEPTED trace={}", notice.traceId());
                    case VALIDATED -> log.info("DODU_DEVICE_PUSH stage=VALIDATED trace={}", notice.traceId());
                    case REJECTED -> log.warn("DODU_DEVICE_PUSH stage=FCM_REJECTED trace={} code={}", notice.traceId(), result.failureCode());
                    case FAILED -> log.warn("DODU_DEVICE_PUSH stage=FAILED trace={} code={}", notice.traceId(), result.failureCode());
                }
            } catch (Exception ignored) {
                log.warn("DODU_DEVICE_PUSH stage=FAILED trace={} code=SEND_FAILED", notice.traceId());
            }
        }
    }
}
