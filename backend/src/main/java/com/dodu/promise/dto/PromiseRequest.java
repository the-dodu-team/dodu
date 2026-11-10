package com.dodu.promise.dto;

import com.dodu.promise.PromiseRequestValidator;

public record PromiseRequest(
        String taskTitle,
        PromiseRequestValidator.ToolType toolType,
        String scheduledAt) { }
