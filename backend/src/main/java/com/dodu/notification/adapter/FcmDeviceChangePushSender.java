package com.dodu.notification.adapter;

import com.dodu.notification.DeviceChangePushSender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dodu.push.fcm.enabled", havingValue = "true")
public class FcmDeviceChangePushSender implements DeviceChangePushSender {
    private final FirebaseMessaging messaging;
    private final boolean dryRun;
    public FcmDeviceChangePushSender(FirebaseMessaging messaging, @Value("${dodu.push.fcm.dry-run:false}") boolean dryRun) {
        this.messaging = messaging; this.dryRun = dryRun;
    }
    @Override
    public Result send(String token) {
        try {
            messaging.send(Message.builder().setToken(token)
                    .setNotification(Notification.builder().setTitle("다른 기기에서 로그인했어요")
                            .setBody("새 기기에서 확인하여 이전 기기의 이용이 종료되었어요.").build())
                    .putData("type", "SESSION_REPLACED")
                    .setWebpushConfig(WebpushConfig.builder().putHeader("TTL", "60").build()).build(), dryRun);
            return new Result(dryRun ? Status.VALIDATED : Status.ACCEPTED, null);
        } catch (FirebaseMessagingException failure) {
            return new Result(Status.REJECTED, failure.getMessagingErrorCode() == null ? "UNKNOWN" : failure.getMessagingErrorCode().name());
        } catch (Exception ignored) {
            return new Result(Status.FAILED, "SEND_FAILED");
        }
    }
}
