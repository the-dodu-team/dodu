package com.dodu.notification;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps scheduling safe until a real Web Push provider contract is configured. */
@Component
@ConditionalOnProperty(name = "dodu.push.fcm.enabled", havingValue = "false", matchIfMissing = true)
public final class NoopPushNotificationSender implements PushNotificationSender {
    @Override
    public PushSendResult send(UUID participantId, UUID promiseId, NotificationSchedulePolicy.NotificationType type) {
        return PushSendResult.unconfigured();
    }
}
