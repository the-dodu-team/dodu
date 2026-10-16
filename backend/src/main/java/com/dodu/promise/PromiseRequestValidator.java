package com.dodu.promise;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class PromiseRequestValidator {
    public enum ToolType { COMPUTER, BOOK }

    public record Request(String taskTitle, ToolType toolType, String scheduledAt) { }

    public record Validated(String taskTitle, ToolType toolType, Instant scheduledAt) { }

    public Validated validate(Request request, Instant now, Instant studyEndsAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(studyEndsAt, "studyEndsAt");
        String title = request.taskTitle() == null ? "" : request.taskTitle().trim();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 30) {
            throw new PromiseValidationException("INVALID_TASK_TITLE");
        }
        if (request.toolType() == null) {
            throw new PromiseValidationException("INVALID_TOOL_TYPE");
        }
        OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(request.scheduledAt());
        } catch (RuntimeException exception) {
            throw new PromiseValidationException("DATETIME_OFFSET_REQUIRED");
        }
        Instant scheduledAt = parsed.toInstant();
        if (scheduledAt.isBefore(now.plus(Duration.ofMinutes(5)))) {
            throw new PromiseValidationException("PROMISE_SCHEDULE_TOO_SOON");
        }
        if (scheduledAt.isAfter(now.plus(Duration.ofHours(72)))) {
            throw new PromiseValidationException("PROMISE_SCHEDULE_TOO_FAR");
        }
        if (!scheduledAt.isBefore(studyEndsAt)) {
            throw new PromiseValidationException("PROMISE_SCHEDULE_AFTER_STUDY_END");
        }
        return new Validated(title, request.toolType(), scheduledAt);
    }

    public static final class PromiseValidationException extends RuntimeException {
        public PromiseValidationException(String code) { super(code); }
    }
}
