package com.dodu.notification;

import java.time.Duration;
import java.time.Instant;

/** 애플리케이션이 소유하는 일시적 Push 실패 재시도 정책. */
public final class PushRetryPolicy {
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10)
    };

    public boolean canRetry(int attemptCount) {
        return attemptCount < BACKOFFS.length;
    }

    public Instant nextAttemptAt(Instant now, int attemptCount) {
        if (!canRetry(attemptCount)) {
            return null;
        }
        return now.plus(BACKOFFS[attemptCount]);
    }
}
