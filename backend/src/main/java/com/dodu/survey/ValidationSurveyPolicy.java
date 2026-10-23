package com.dodu.survey;

import java.time.Duration;
import java.time.Instant;

public final class ValidationSurveyPolicy {
    public enum Eligibility { AUTH_COMPLETED, AUTH_INCOMPLETE, NOT_ELIGIBLE }
    public enum Status { PENDING, PROMPTED, SUBMITTED, SKIPPED, EXPIRED }

    public Eligibility eligibility(String promiseOutcome) {
        if ("AuthCompleted".equals(promiseOutcome)) return Eligibility.AUTH_COMPLETED;
        if ("AuthIncomplete".equals(promiseOutcome)) return Eligibility.AUTH_INCOMPLETE;
        return Eligibility.NOT_ELIGIBLE;
    }

    public boolean canRespond(Eligibility eligibility, Instant availableAt, Instant expiresAt, Instant now) {
        return eligibility != Eligibility.NOT_ELIGIBLE
                && availableAt != null && expiresAt != null
                && !now.isBefore(availableAt) && now.isBefore(expiresAt);
    }

    public Instant expiresAt(Instant availableAt) {
        if (availableAt == null) throw new IllegalArgumentException("availableAt must not be null");
        return availableAt.plus(Duration.ofHours(24));
    }
}
