package com.dodu.notification;

import java.util.UUID;

/** Provider boundary. The provider must receive a resolvable subscription, not a stored secret. */
public interface PushNotificationSender {
    PushSendResult send(UUID participantId, UUID promiseId, NotificationSchedulePolicy.NotificationType type);

    record PushSendResult(Status status, String providerMessageId, String failureCode) {
        public static PushSendResult unconfigured() {
            return new PushSendResult(Status.UNCONFIGURED, null, "PUSH_PROVIDER_UNCONFIGURED");
        }
    }

    enum Status { SENT, FAILED, UNCONFIGURED }
}
