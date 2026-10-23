package com.dodu.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(AccessFailureRateLimiterIntegrationTests.ContainerConfiguration.class)
class AccessFailureRateLimiterIntegrationTests {
    @Autowired JdbcTemplate jdbc;

    private final Instant start = Instant.parse("2026-10-18T00:00:00Z");
    private AccessFailureRateLimiter limiter;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from access_failure_rate_limit");
        limiter = new AccessFailureRateLimiter(jdbc, Clock.fixed(start, ZoneOffset.UTC));
    }

    @Test
    void blocksOnTenthFailureWithoutExtendingBlockedUntil() {
        for (int i = 0; i < 9; i++) {
            assertFalse(limiter.recordFailure("198.51.100.20").blocked());
        }
        AccessFailureRateLimiter.BlockStatus blocked = limiter.recordFailure("198.51.100.20");
        assertTrue(blocked.blocked());
        assertEquals(start.plusSeconds(15 * 60L), blocked.blockedUntil());
        assertEquals(blocked.blockedUntil(), limiter.recordFailure("198.51.100.20").blockedUntil());
    }

    @Test
    void successfulAccessCanClearFailureWindow() {
        limiter.recordFailure("198.51.100.21");
        limiter.clear("198.51.100.21");
        assertFalse(limiter.status("198.51.100.21").blocked());
        assertEquals(1, limiter.recordFailure("198.51.100.21").blocked() ? 0 : 1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
