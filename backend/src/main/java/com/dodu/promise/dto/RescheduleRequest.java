package com.dodu.promise.dto;

public record RescheduleRequest(
        String scheduledAt,
        long expectedVersion) { }

