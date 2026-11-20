package com.dodu.intervention.dto;

import com.dodu.intervention.domain.Result;

/** detectedLabel: AI가 본 물건(laptop/book). COMPLETED일 때 필수이며 약속 도구와 비교한다. */
public record ResultRequest(
        Result result,
        String rejectionReason,
        String technicalErrorType,
        String modelVersion,
        String detectedLabel) { }
