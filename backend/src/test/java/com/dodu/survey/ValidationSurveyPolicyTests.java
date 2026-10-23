package com.dodu.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ValidationSurveyPolicyTests {
    private static final Instant AVAILABLE = Instant.parse("2026-10-16T00:00:00Z");
    private final ValidationSurveyPolicy policy = new ValidationSurveyPolicy();

    @Test
    void onlyCompletedOrIncompleteAuthIsEligible() {
        assertEquals(ValidationSurveyPolicy.Eligibility.AUTH_COMPLETED, policy.eligibility("AuthCompleted"));
        assertEquals(ValidationSurveyPolicy.Eligibility.AUTH_INCOMPLETE, policy.eligibility("AuthIncomplete"));
        assertEquals(ValidationSurveyPolicy.Eligibility.NOT_ELIGIBLE, policy.eligibility("Cancelled"));
        assertEquals(AVAILABLE.plusSeconds(86400), policy.expiresAt(AVAILABLE));
    }

    @Test
    void responseWindowIsAvailableAtInclusiveAndExpiresAtExclusive() {
        var eligibility = ValidationSurveyPolicy.Eligibility.AUTH_INCOMPLETE;
        assertTrue(policy.canRespond(eligibility, AVAILABLE, policy.expiresAt(AVAILABLE), AVAILABLE));
        assertFalse(policy.canRespond(eligibility, AVAILABLE, policy.expiresAt(AVAILABLE), policy.expiresAt(AVAILABLE)));
        assertFalse(policy.canRespond(ValidationSurveyPolicy.Eligibility.NOT_ELIGIBLE, AVAILABLE, policy.expiresAt(AVAILABLE), AVAILABLE));
    }
}
