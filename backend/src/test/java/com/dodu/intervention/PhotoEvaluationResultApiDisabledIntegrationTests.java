package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 기본 설정에서는 참가자가 판정 결과를 직접 넣는 결과 API가 닫혀 있다(PR #55 리뷰). */
@SpringBootTest
@Import(PhotoEvaluationResultApiDisabledIntegrationTests.ContainerConfiguration.class)
class PhotoEvaluationResultApiDisabledIntegrationTests {
    @Autowired
    private WebApplicationContext context;

    @Test
    void resultApiIsClosedByDefault() throws Exception {
        assertTrue(context.getBeansOfType(PhotoEvaluationResultController.class).isEmpty());
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        mockMvc.perform(post("/api/promises/" + UUID.randomUUID() + "/photo-attempts/" + UUID.randomUUID() + "/result")
                        .header("Authorization", "Bearer any").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\",\"detectedLabel\":\"laptop\"}"))
                .andExpect(status().is4xxClientError());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
