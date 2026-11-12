package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class AiPhotoEvaluationRecoveryHandlerContextTests {
    @Test
    void enabledWorkerStartsWithInjectedConstructorAndOptionalStorage() {
        try (var context = context(true)) {
            // Production uses Jackson 3; no Jackson 2 ObjectMapper bean is supplied here.
            context.refresh();
            assertSame(context.getBean(AiPhotoEvaluationRecoveryHandler.class),
                    context.getBean(PhotoEvaluationRecoveryHandler.class));
            assertEquals(0, context.getBeansOfType(ObjectMapper.class).size());
        }
    }

    @Test
    void disabledWorkerDoesNotRequireDependencies() {
        try (var context = context(false)) {
            context.refresh();
            assertEquals(0, context.getBeansOfType(PhotoEvaluationRecoveryHandler.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test", Map.of("dodu.ai.enabled", Boolean.toString(enabled))));
        context.register(AiPhotoEvaluationRecoveryHandler.class);
        return context;
    }
}
