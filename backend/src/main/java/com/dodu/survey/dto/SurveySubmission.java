package com.dodu.survey.dto;

public record SurveySubmission(
        Boolean actualStarted,
        String startOrder,
        String startDelayBucket,
        String noStartReason) {}

