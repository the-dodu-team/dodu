package com.dodu.intervention.dto;

import com.dodu.intervention.domain.Result;

public record ResultRequest(
        Result result,
        String rejectionReason,
        String technicalErrorType,
        String modelVersion) { }

