package com.dodu.notification;

import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.service.ParticipantAuthService;
import org.springframework.stereotype.Component;
import java.time.Clock;

/** Controller 인증 이후 교체·철회된 세션이 뒤늦게 구독을 되살리지 못하게 한다. */
@Component
public class NotificationSubscriptionGuard {
    private final NotificationSubscriptionRepository subscriptions;
    private final ParticipantAccessRepository repository;
    private final Clock clock;

    public NotificationSubscriptionGuard(NotificationSubscriptionRepository subscriptions, ParticipantAccessRepository repository, Clock clock) {
        this.subscriptions = subscriptions;
        this.repository = repository;
        this.clock = clock;
    }

    // 호출자의 구독 트랜잭션 안에서 입장·철회와 같은 참가자 행 잠금을 사용한다.
    public void lockAndCheck(SessionContext context) {
        String hash = subscriptions.lockParticipantAndFindSessionHash(context.participant().id(), context.session().id());
        SessionContext current = repository.findSessionByTokenHash(hash).orElseThrow(ParticipantAuthService::accessRequired);
        if (current.check(clock.instant()) != com.dodu.participant.domain.SessionAccessResult.ALLOWED) {
            throw ParticipantAuthService.accessRequired();
        }
    }

    public void lockToken(String hash) {
        // 같은 브라우저 토큰을 서로 다른 참가자가 동시에 등록해도 소유자는 하나다.
        subscriptions.lockToken(hash);
    }
}
