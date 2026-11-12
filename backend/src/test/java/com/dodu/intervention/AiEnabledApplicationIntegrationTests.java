package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"dodu.ai.enabled=true", "dodu.push.fcm.enabled=false",
        "dodu.photo-storage.provider=none"})
@Import(AiEnabledApplicationIntegrationTests.DatabaseConfiguration.class)
class AiEnabledApplicationIntegrationTests {
    @Autowired
    private AiPhotoEvaluationRecoveryHandler handler;

    @Test
    void applicationStartsWithAiEnabledAndRealDatabaseWithoutManualJsonBean() {
        assertNotNull(handler);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }
}
