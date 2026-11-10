package com.dodu.intervention.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultResponse(
        UUID attemptId,
        UUID promiseId,
        String processingStatus,
        String decisionResult,
        Instant decidedAt) { }

