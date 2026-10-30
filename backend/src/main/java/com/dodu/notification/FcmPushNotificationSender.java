package com.dodu.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dodu.push.fcm.enabled", havingValue = "true")
public class FcmPushNotificationSender implements PushNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationSender.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final FirebaseMessaging messaging;
    private final boolean dryRun;

    public FcmPushNotificationSender(JdbcTemplate jdbc, Clock clock, FirebaseMessaging messaging,
                                     @Value("${dodu.push.fcm.dry-run:false}") boolean dryRun) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.messaging = messaging;
        this.dryRun = dryRun;
    }

    @Override
    public PushSendResult send(UUID participantId, UUID promiseId,
                               NotificationSchedulePolicy.NotificationType type) {
        Target target = findActiveTarget(participantId);
        if (target == null) {
            return PushSendResult.undeliverable("NO_ACTIVE_SUBSCRIPTION");
        }
        Message message = Message.builder()
                .setToken(target.token())
                .setNotification(Notification.builder()
                        .setTitle(title(type)).setBody(body(type)).build())
                .putData("type", type.name())
                .putData("promiseId", promiseId.toString())
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("TTL", String.valueOf(ttlSeconds(type)))
                        .putHeader("Urgency", "high")
                        .build())
                .build();
        try {
            String messageId = messaging.send(message, dryRun);
            return new PushSendResult(Status.SENT, messageId, null);
        } catch (FirebaseMessagingException e) {
            return handleFailure(target, promiseId, e);
        }
    }

    private PushSendResult handleFailure(Target target, UUID promiseId, FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        String name = code != null ? code.name() : String.valueOf(e.getErrorCode());
        // 로그에는 토큰과 예외 메시지를 남기지 않는다.
        log.warn("FCM send failed code={} promiseId={}", name, promiseId);
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            deactivate(target.subscriptionId());
            return PushSendResult.undeliverable(name);
        }
        if (code == MessagingErrorCode.INVALID_ARGUMENT) {
            return PushSendResult.undeliverable(name);
        }
        return new PushSendResult(Status.FAILED, null, name);   // 일시 오류는 재시도 정책이 처리
    }

    private Target findActiveTarget(UUID participantId) {
        List<Target> targets = jdbc.query("""
                select id, push_token from notification_subscription
                 where participant_id = ? and lifecycle_status = 'Active' and push_token is not null
                 order by activated_at desc limit 1
                """, (rs, rowNum) -> new Target(rs.getObject("id", UUID.class), rs.getString("push_token")),
                participantId);
        return targets.isEmpty() ? null : targets.get(0);
    }

    private void deactivate(UUID subscriptionId) {
        jdbc.update("""
                update notification_subscription
                   set lifecycle_status = 'Inactive', deactivated_at = ?, push_token = null
                 where id = ? and lifecycle_status = 'Active'
                """, Timestamp.from(clock.instant()), subscriptionId);
    }

    // 문구는 임시. PO·디자인 확인 후 수정할 예정.
    private static String title(NotificationSchedulePolicy.NotificationType type) {
        return switch (type) {
            case START -> "약속한 시간이에요";
            case REMINDER_10, REMINDER_20 -> "아직 시작 전이에요";
            case SURVEY_REMINDER -> "오늘의 시작 경험을 알려주세요";
        };
    }

    private static String body(NotificationSchedulePolicy.NotificationType type) {
        return switch (type) {
            case START -> "작업도구가 있는 곳으로 이동해 볼까요?";
            case REMINDER_10, REMINDER_20 -> "지금 인증하면 시작할 수 있어요.";
            case SURVEY_REMINDER -> "1분이면 끝나요. 건너뛰어도 괜찮아요.";
        };
    }

    // 알림이 늦게 도착하지 않도록 만료 시간을 짧게 둔다.
    private static long ttlSeconds(NotificationSchedulePolicy.NotificationType type) {
        return type == NotificationSchedulePolicy.NotificationType.SURVEY_REMINDER ? 3600 : 600;
    }

    private record Target(UUID subscriptionId, String token) { }
}