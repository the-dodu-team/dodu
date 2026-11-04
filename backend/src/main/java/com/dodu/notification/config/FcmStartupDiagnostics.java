package com.dodu.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 설정값·경로·자격정보를 출력하지 않고 발송 활성화/시험 여부만 남긴다. */
@Component
public class FcmStartupDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(FcmStartupDiagnostics.class);
    private final boolean enabled;
    private final boolean dryRun;
    public FcmStartupDiagnostics(@Value("${dodu.push.fcm.enabled:false}") boolean enabled,
                                 @Value("${dodu.push.fcm.dry-run:false}") boolean dryRun) {
        this.enabled = enabled; this.dryRun = dryRun;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        log.info("DODU_FCM_CONFIG enabled={} dryRun={}", enabled, dryRun);
    }
}
