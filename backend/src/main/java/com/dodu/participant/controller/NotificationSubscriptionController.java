package com.dodu.participant.controller;
import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import com.dodu.notification.NotificationSubscriptionService;
import com.dodu.notification.NotificationSubscriptionService.SubscriptionResult;
import java.time.Clock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** 세션 구독의 HTTP 경계. 정책·트랜잭션·SQL은 서비스와 저장소에 둔다. */
@RestController
@Tag(name = "알림 구독")
@RequestMapping("/api/participant/notification-subscription")
public class NotificationSubscriptionController {
    private final NotificationSubscriptionService service;
    private final Clock clock;
    public NotificationSubscriptionController(NotificationSubscriptionService service, Clock clock) {
        this.service = service; this.clock = clock;
    }
    @Operation(summary = "알림 구독 등록")
    @PutMapping
    public ApiResponse<SubscriptionResult> subscribe(SessionContext context, @RequestBody SubscriptionRequest request) {
        return ApiResponse.success(service.subscribe(context, request == null ? null : request.subscriptionKey()), ApiTime.now(clock));
    }
    @Operation(summary = "알림 구독 해제")
    @DeleteMapping
    public ApiResponse<SubscriptionResult> unsubscribe(SessionContext context) {
        return ApiResponse.success(service.unsubscribe(context), ApiTime.now(clock));
    }
    public record SubscriptionRequest(String subscriptionKey) { }
}
