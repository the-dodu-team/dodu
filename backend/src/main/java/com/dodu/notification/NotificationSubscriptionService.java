package com.dodu.notification;
import com.dodu.access.AccessCredentialHasher;
import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.SessionContext;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class NotificationSubscriptionService {
    private final NotificationSubscriptionRepository repository;
    private final NotificationSubscriptionGuard guard;
    private final Clock clock;
    private final AccessCredentialHasher hasher;
    public NotificationSubscriptionService(NotificationSubscriptionRepository repository, NotificationSubscriptionGuard guard, Clock clock) {
        this.repository = repository; this.guard = guard; this.clock = clock;
        // 기존 해시 계약을 보존하며 이 작업에서 pepper·토큰을 회전하지 않는다.
        this.hasher = new AccessCredentialHasher(System.getenv().getOrDefault("DODU_CREDENTIAL_PEPPER", "local-development-only"));
    }
    @Transactional
    public SubscriptionResult subscribe(SessionContext context, String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new BusinessException(ErrorType.VALIDATION, "SUBSCRIPTION_KEY_INVALID", "요청이 올바르지 않아요.");
        }
        Instant now = clock.instant();
        String hash = hasher.hash(token);
        guard.lockAndCheck(context);
        guard.lockToken(hash);
        repository.deactivateOtherTargets(context.participant().id(), hash, now);
        repository.activateExisting(context.participant().id(), context.session().id(), hash, token, now);
        if (repository.findOwned(context.participant().id(), hash) == null) {
            repository.insert(context.participant().id(), context.session().id(), hash, token, now);
        }
        return new SubscriptionResult("Active", now);
    }
    @Transactional
    public SubscriptionResult unsubscribe(SessionContext context) {
        guard.lockAndCheck(context);
        Instant now = clock.instant();
        repository.deactivateSession(context.participant().id(), context.session().id(), now);
        return new SubscriptionResult("Inactive", now);
    }
    @io.swagger.v3.oas.annotations.media.Schema(name = "SubscriptionResponse")
    public record SubscriptionResult(String lifecycleStatus, Instant changedAt) { }
}
