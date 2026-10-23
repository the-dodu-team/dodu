package com.dodu.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(ParticipantAccessRateLimitIntegrationTests.ContainerConfiguration.class)
class ParticipantAccessRateLimitIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("delete from access_failure_rate_limit");
    }

    @Test
    void tenthInvalidParticipantCodeReturnsTemporaryBlockAndRetryAfter() throws Exception {
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post("/api/access/enter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"invalid\",\"deviceKey\":\"device\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/access/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invalid\",\"deviceKey\":\"device\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
