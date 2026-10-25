package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiPhotoEvaluationResponseMapperTests {
    private final AiPhotoEvaluationResponseMapper mapper =
            new AiPhotoEvaluationResponseMapper(new ObjectMapper());

    @Test
    void laptopAndBookAreCompleted() {
        var laptop = mapper.map(200, "{\"candidates\":[{\"label\":\"laptop\"}],\"model_version\":\"mvp-1\"}");
        var book = mapper.map(200, "{\"label\":\"book\"}");

        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.COMPLETED, laptop.outcome());
        assertEquals("mvp-1", laptop.modelVersion());
        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.COMPLETED, book.outcome());
    }

    @Test
    void otherIsRejectedWithoutUsingConfidence() {
        var result = mapper.map(200, "{\"candidates\":[{\"label\":\"other\",\"confidence\":0.99}]}");

        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.REJECTED, result.outcome());
        assertEquals("AI_OTHER", result.errorCode());
        assertNull(result.modelVersion());
    }

    @Test
    void transientAndInvalidResponsesAreRetryableButOtherHttpErrorsAreTechnical() {
        var serverError = mapper.map(503, "unavailable");
        var invalid = mapper.map(200, "{\"label\":\"tablet\"}");
        var unauthorized = mapper.map(401, "unauthorized");

        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.RETRYABLE_FAILURE, serverError.outcome());
        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.RETRYABLE_FAILURE, invalid.outcome());
        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.TECHNICAL_FAILURE, unauthorized.outcome());
    }
}
