package com.dodu.survey.dto;

import java.time.Instant;
import java.util.UUID;

public record SurveyResponse(
        UUID id,
        String questionnaireVersion,
        String lifecycleStatus,
        String eligibilityStatus,

        Boolean actualStarted,
        String startOrder,
        String startDelayBucket,
        String noStartReason,

        Instant availableAt,
        Instant expiresAt,
        Instant submittedAt) {}
