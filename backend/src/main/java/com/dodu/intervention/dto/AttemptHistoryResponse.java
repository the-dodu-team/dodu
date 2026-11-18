package com.dodu.intervention.dto;

import java.time.Instant;
import java.util.UUID;

public record AttemptHistoryResponse(
        UUID attemptId,
        String toolType,
        String processingStatus,
        String decisionResult,
        String rejectionReason,

        String technicalErrorType,
        String modelVersion,

        Instant requestReceivedAt,
        Instant validReceivedAt,

        Instant decidedAt) { }
