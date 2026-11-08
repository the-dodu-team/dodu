package com.dodu.notification;
import java.time.Instant;
import java.util.UUID;
/** 공개 조회 모델. 토큰·구독 원장을 포함하지 않는다. */
@io.swagger.v3.oas.annotations.media.Schema(name = "NotificationResponse")
public record NotificationView(UUID notificationId, UUID promiseId, String notificationType,
                               Instant scheduledAt, Instant sentAt, Instant readAt) { }
