package com.dodu.intervention.dto;

import java.time.Instant;
import java.util.UUID;

public record PhotoAttemptResponse(
        UUID attemptId,
        UUID promiseId,
        String processingStatus,
        Instant receivedAt) { }

