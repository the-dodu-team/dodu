package com.dodu.notification;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Keeps scheduling safe until a real Web Push provider contract is configured. */
@Component
public final class NoopPushNotificationSender implements PushNotificationSender {
    @Override
    public PushSendResult send(UUID participantId, UUID promiseId, NotificationSchedulePolicy.NotificationType type) {
        return PushSendResult.unconfigured();
    }
}
