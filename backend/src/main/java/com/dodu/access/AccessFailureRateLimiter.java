package com.dodu.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared, database-backed rate limit for participant-code failures. */
@Component
public class AccessFailureRateLimiter {
    static final int MAX_FAILURES = 10;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public AccessFailureRateLimiter(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    AccessFailureRateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BlockStatus status(String ipAddress) {
        String ip = normalize(ipAddress);
        Instant now = clock.instant();
        Instant blockedUntil = jdbc.query(
                "select blocked_until from access_failure_rate_limit where ip_address = ?",
                rs -> rs.next() && rs.getTimestamp("blocked_until") != null
                        ? rs.getTimestamp("blocked_until").toInstant() : null,
                ip);
        return blockedUntil != null && now.isBefore(blockedUntil)
                ? new BlockStatus(true, blockedUntil) : new BlockStatus(false, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BlockStatus recordFailure(String ipAddress) {
        String ip = normalize(ipAddress);
        Instant now = clock.instant();
        jdbc.update("""
                insert into access_failure_rate_limit(ip_address, window_started_at, failed_attempts, updated_at)
                values (?, ?, 0, ?)
                on conflict (ip_address) do nothing
                """, ip, timestamp(now), timestamp(now));
        FailureRow current = jdbc.query(
                "select window_started_at, failed_attempts, blocked_until "
                        + "from access_failure_rate_limit where ip_address = ? for update",
                rs -> rs.next() ? new FailureRow(
                        rs.getTimestamp("window_started_at").toInstant(), rs.getInt("failed_attempts"),
                        rs.getTimestamp("blocked_until") == null ? null : rs.getTimestamp("blocked_until").toInstant()) : null,
                ip);
        if (current != null && current.blockedUntil() != null && now.isBefore(current.blockedUntil())) {
            return new BlockStatus(true, current.blockedUntil());
        }

        boolean windowExpired = current == null || !now.isBefore(current.windowStartedAt().plus(FAILURE_WINDOW));
        int attempts = windowExpired ? 1 : current.failedAttempts() + 1;
        Instant windowStartedAt = windowExpired ? now : current.windowStartedAt();
        Instant blockedUntil = attempts >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
        jdbc.update("""
                insert into access_failure_rate_limit(ip_address, window_started_at, failed_attempts, blocked_until, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict (ip_address) do update set
                    window_started_at = excluded.window_started_at,
                    failed_attempts = excluded.failed_attempts,
                    blocked_until = excluded.blocked_until,
                    updated_at = excluded.updated_at
                """, ip, timestamp(windowStartedAt), attempts,
                blockedUntil == null ? null : timestamp(blockedUntil), timestamp(now));
        return new BlockStatus(blockedUntil != null, blockedUntil);
    }

    public void clear(String ipAddress) {
        jdbc.update("delete from access_failure_rate_limit where ip_address = ?", normalize(ipAddress));
    }

    private static String normalize(String ipAddress) {
        return ipAddress == null || ipAddress.isBlank() || ipAddress.length() > 64 ? "unknown" : ipAddress;
    }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    public record BlockStatus(boolean blocked, Instant blockedUntil) { }
    private record FailureRow(Instant windowStartedAt, int failedAttempts, Instant blockedUntil) { }
}
