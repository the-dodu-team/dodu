package com.dodu.notification;
import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@Tag(name = "알림")
@RequestMapping("/api/participant/notifications")
public class NotificationController {
    private final NotificationQueryService service;
    private final Clock clock;
    public NotificationController(NotificationQueryService service, Clock clock) {
        this.service = service; this.clock = clock;
    }
    @Operation(summary = "내 알림 목록 조회")
    @GetMapping
    public ApiResponse<List<NotificationView>> list(SessionContext context) {
        return ApiResponse.success(service.list(context.participant().id()), ApiTime.now(clock));
    }
    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationView> markRead(@PathVariable UUID notificationId, SessionContext context) {
        return ApiResponse.success(service.markRead(context.participant().id(), notificationId), ApiTime.now(clock));
    }
}
