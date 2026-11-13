package com.dodu.promise.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PromiseDailySlotPolicyTests {

    @Test
    void onlyUnverifiedOrTechnicalFailureReleasesTheDate() {
        assertTrue(PromiseDailySlotPolicy.isReplaceable("AuthIncomplete", "AuthIncomplete"));
        assertTrue(PromiseDailySlotPolicy.isReplaceable("Closed", "TechFailed"));
        assertTrue(PromiseDailySlotPolicy.isReplaceable("AuthIncomplete", null));
    }

    @Test
    void verifiedCancelledWithdrawnOrActivePromiseKeepsTheDate() {
        assertFalse(PromiseDailySlotPolicy.isReplaceable("AuthCompleted", "AuthCompleted"));
        assertFalse(PromiseDailySlotPolicy.isReplaceable("Closed", "Cancelled"));
        assertFalse(PromiseDailySlotPolicy.isReplaceable("Closed", "Withdrawn"));
        assertFalse(PromiseDailySlotPolicy.isReplaceable("Closed", "Permission"));
        assertFalse(PromiseDailySlotPolicy.isReplaceable("Scheduled", null));
        assertFalse(PromiseDailySlotPolicy.isReplaceable("Evaluating", null));
    }
}
