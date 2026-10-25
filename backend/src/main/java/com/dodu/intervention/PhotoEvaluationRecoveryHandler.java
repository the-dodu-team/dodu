package com.dodu.intervention;

import java.util.UUID;

/** Adapter boundary for the external AI worker. It never receives raw photo bytes here. */
public interface PhotoEvaluationRecoveryHandler {
    RecoveryResult recover(RecoveryJob job);

    record RecoveryJob(UUID jobId, UUID attemptId, UUID promiseId, int attemptCount) { }

    record RecoveryResult(Outcome outcome, String errorCode, String modelVersion) {
        public static RecoveryResult retryable(String errorCode) {
            return new RecoveryResult(Outcome.RETRYABLE_FAILURE, errorCode, null);
        }

        public static RecoveryResult completed(String modelVersion) {
            return new RecoveryResult(Outcome.COMPLETED, null, modelVersion);
        }

        public static RecoveryResult rejected(String errorCode, String modelVersion) {
            return new RecoveryResult(Outcome.REJECTED, errorCode, modelVersion);
        }
    }

    enum Outcome { COMPLETED, REJECTED, RETRYABLE_FAILURE, TECHNICAL_FAILURE }
}
