package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AcceptedPhotoEvaluationPolicyTests {
    private static final Instant START = Instant.parse("2026-10-04T00:00:00Z");
    private static final Instant DEADLINE = START.plus(Duration.ofMinutes(30));

    @Test
    void acceptedSubmissionRemainsEligibleAfterNewSubmissionsAndInterventionsClose() {
        InterventionWindowPolicy afterEnd = new InterventionWindowPolicy(
                Clock.fixed(DEADLINE.plus(Duration.ofDays(1)), ZoneOffset.UTC));
        assertEquals(InterventionWindowPolicy.PhotoSubmissionDecision.WINDOW_EXPIRED,
                afterEnd.validateNewPhotoSubmission(START));
        assertEquals(false, afterEnd.isNewInterventionOpen(DEADLINE));
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.ALLOWED,
                AcceptedPhotoEvaluationPolicy.validate(START, DEADLINE.minusNanos(1),
                        PhotoAttempt.ProcessingStatus.EVALUATING, false));
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.ALLOWED,
                AcceptedPhotoEvaluationPolicy.validate(START, START,
                        PhotoAttempt.ProcessingStatus.EVALUATING, false));
    }

    @Test
    void receiptOutsideWindowOrMissingValidAcceptanceIsRejected() {
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.OUTSIDE_SUBMISSION_WINDOW,
                AcceptedPhotoEvaluationPolicy.validate(START, START.minusNanos(1),
                        PhotoAttempt.ProcessingStatus.EVALUATING, false));
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.OUTSIDE_SUBMISSION_WINDOW,
                AcceptedPhotoEvaluationPolicy.validate(START, DEADLINE,
                        PhotoAttempt.ProcessingStatus.EVALUATING, false));
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.NOT_VALIDLY_RECEIVED,
                AcceptedPhotoEvaluationPolicy.validate(START, null,
                        PhotoAttempt.ProcessingStatus.EVALUATING, false));
    }

    @Test
    void withdrawalAndNonEvaluatingAttemptsCannotApplyResults() {
        assertEquals(AcceptedPhotoEvaluationPolicy.Decision.PARTICIPANT_WITHDRAWN,
                AcceptedPhotoEvaluationPolicy.validate(START, START,
                        PhotoAttempt.ProcessingStatus.EVALUATING, true));
        for (PhotoAttempt.ProcessingStatus status : PhotoAttempt.ProcessingStatus.values()) {
            if (status != PhotoAttempt.ProcessingStatus.EVALUATING) {
                assertEquals(AcceptedPhotoEvaluationPolicy.Decision.NOT_EVALUATING,
                        AcceptedPhotoEvaluationPolicy.validate(START, START, status, false));
            }
        }
    }
}
