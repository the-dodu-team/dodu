package com.dodu.promise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PromiseRequestValidatorTests {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final Instant STUDY_END = Instant.parse("2026-10-17T00:00:00Z");
    private final PromiseRequestValidator validator = new PromiseRequestValidator();

    @Test
    void trimsTitleAndParsesOffsetDateTime() {
        var result = validator.validate(new PromiseRequestValidator.Request(
                "  read  ", PromiseRequestValidator.ToolType.BOOK, "2026-10-16T15:00:00+09:00"), NOW, STUDY_END);
        assertEquals("read", result.taskTitle());
        assertEquals(Instant.parse("2026-10-16T06:00:00Z"), result.scheduledAt());
    }

    @Test
    void rejectsInvalidBoundaries() {
        assertCode("INVALID_TASK_TITLE", new PromiseRequestValidator.Request(" ", PromiseRequestValidator.ToolType.BOOK, "2026-10-16T01:00:00+09:00"));
        assertCode("DATETIME_OFFSET_REQUIRED", new PromiseRequestValidator.Request("read", PromiseRequestValidator.ToolType.BOOK, "2026-10-16T01:00:00"));
        assertCode("PROMISE_SCHEDULE_TOO_SOON", new PromiseRequestValidator.Request("read", PromiseRequestValidator.ToolType.BOOK, "2026-10-16T00:04:59Z"));
        assertCode("PROMISE_SCHEDULE_TOO_FAR", new PromiseRequestValidator.Request("read", PromiseRequestValidator.ToolType.BOOK, "2026-10-19T00:00:01Z"));
        assertCode("PROMISE_SCHEDULE_AFTER_STUDY_END", new PromiseRequestValidator.Request("read", PromiseRequestValidator.ToolType.BOOK, "2026-10-17T00:00:00Z"));
    }

    private void assertCode(String code, PromiseRequestValidator.Request request) {
        var error = assertThrows(PromiseRequestValidator.PromiseValidationException.class,
                () -> validator.validate(request, NOW, STUDY_END));
        assertEquals(code, error.getMessage());
    }
}
