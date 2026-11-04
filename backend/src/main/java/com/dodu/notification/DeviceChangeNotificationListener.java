package com.dodu.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
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
    private final FirebaseMessaging messaging;
    private final TaskExecutor executor;
    private final boolean dryRun;
    public DeviceChangeNotificationListener(FirebaseMessaging messaging,
            @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Value("${dodu.push.fcm.dry-run:false}") boolean dryRun) {
        this.messaging = messaging; this.executor = executor; this.dryRun = dryRun;
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
                messaging.send(Message.builder().setToken(token)
                        .setNotification(Notification.builder().setTitle("다른 기기에서 로그인했어요")
                                .setBody("새 기기에서 확인하여 이전 기기의 이용이 종료되었어요.").build())
                        .putData("type", "SESSION_REPLACED")
                        .setWebpushConfig(WebpushConfig.builder().putHeader("TTL", "60").build()).build(), dryRun);
                log.info("DODU_DEVICE_PUSH stage={} trace={}", dryRun ? "VALIDATED" : "FCM_ACCEPTED", notice.traceId());
            } catch (FirebaseMessagingException failure) {
                String code = failure.getMessagingErrorCode() == null ? "UNKNOWN" : failure.getMessagingErrorCode().name();
                log.warn("DODU_DEVICE_PUSH stage=FCM_REJECTED trace={} code={}", notice.traceId(), code);
            } catch (Exception ignored) {
                log.warn("DODU_DEVICE_PUSH stage=FAILED trace={} code=SEND_FAILED", notice.traceId());
            }
        }
    }
}
