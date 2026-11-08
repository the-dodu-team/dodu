package com.dodu.notification;
import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class NotificationQueryService {
    private final NotificationRepository repository;
    private final Clock clock;
    public NotificationQueryService(NotificationRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }
    public List<NotificationView> list(UUID participantId) { return repository.listSentInApp(participantId); }
    public NotificationView markRead(UUID participantId, UUID notificationId) {
        if (repository.markOwnedSentAsRead(participantId, notificationId, clock.instant()) != 1) {
            throw new BusinessException(ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", "찾을 수 없어요.");
        }
        // 기존 update → 조회 순서·read_at 멱등성을 보존하며 새 트랜잭션을 추가하지 않는다.
        return repository.findOwned(participantId, notificationId);
    }
}
