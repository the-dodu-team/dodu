package com.dodu.participant.controller;

import com.dodu.access.AccessCredentialHasher;
import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 세션에 종속된 알림 구독의 등록·해지를 담당한다. 실제 Push 발송은 별도 worker 범위다. */
@RestController
@Tag(name = "알림 구독")
@RequestMapping("/api/participant/notification-subscription")
public class NotificationSubscriptionController {
    private static final int MAX_SUBSCRIPTION_KEY_LENGTH = 4096;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AccessCredentialHasher hasher;

    public NotificationSubscriptionController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.hasher = new AccessCredentialHasher(System.getenv().getOrDefault(
                "DODU_CREDENTIAL_PEPPER", "local-development-only"));
    }

    @Operation(summary = "알림 구독 등록")
    @PutMapping
    @Transactional
    public ApiResponse<SubscriptionResponse> subscribe(SessionContext context,
                                                        @RequestBody SubscriptionRequest request) {
        String subscriptionKey = validate(request);
        Instant now = clock.instant();
        String subscriptionHash = hasher.hash(subscriptionKey);

        jdbc.update("""
                update notification_subscription
                   set participant_session_id = ?, lifecycle_status = 'Active',
                       activated_at = ?, last_seen_at = ?, deactivated_at = null,
                       push_token = ?
                 where participant_id = ? and subscription_key_hash = ?
                """, context.session().id(), timestamp(now), timestamp(now),
                subscriptionKey, context.participant().id(), subscriptionHash);

        UUID existing = jdbc.query("""
                select id from notification_subscription
                 where participant_id = ? and subscription_key_hash = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                context.participant().id(), subscriptionHash);
        if (existing == null) {
            jdbc.update("""
                    update notification_subscription
                       set lifecycle_status = 'Inactive', deactivated_at = ?, push_token = null
                     where participant_id = ? and lifecycle_status = 'Active'
                    """, timestamp(now), context.participant().id());
            jdbc.update("""
                    insert into notification_subscription
                        (participant_id, participant_session_id, subscription_key_hash, push_token,
                         lifecycle_status, activated_at, last_seen_at)
                    values (?, ?, ?, ?, 'Active', ?, ?)
                    """, context.participant().id(), context.session().id(), subscriptionHash,
                    subscriptionKey, timestamp(now), timestamp(now));
        }
        return ApiResponse.success(new SubscriptionResponse("Active", now), ApiTime.now(clock));
    }

    @Operation(summary = "알림 구독 해제")
    @DeleteMapping
    @Transactional
    public ApiResponse<SubscriptionResponse> unsubscribe(SessionContext context) {
        Instant now = clock.instant();
        jdbc.update("""
                update notification_subscription
                   set lifecycle_status = 'Inactive', deactivated_at = ?, last_seen_at = ?,
                       push_token = null
                 where participant_id = ? and participant_session_id = ?
                   and lifecycle_status = 'Active'
                """, timestamp(now), timestamp(now), context.participant().id(), context.session().id());
        return ApiResponse.success(new SubscriptionResponse("Inactive", now), ApiTime.now(clock));
    }

    private static String validate(SubscriptionRequest request) {
        if (request == null || request.subscriptionKey() == null
                || request.subscriptionKey().isBlank()
                || request.subscriptionKey().length() > MAX_SUBSCRIPTION_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_KEY_INVALID");
        }
        return request.subscriptionKey();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    public record SubscriptionRequest(String subscriptionKey) { }
    public record SubscriptionResponse(String lifecycleStatus, Instant changedAt) { }
}
