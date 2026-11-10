package com.dodu.promise.dto;

import com.dodu.promise.PromiseRequestValidator;

import java.time.Instant;
import java.util.UUID;

public record PromiseResponse(
        UUID promiseId,
        String taskTitle,
        PromiseRequestValidator.ToolType toolType,
        Instant scheduledAt,
        long version) { }

