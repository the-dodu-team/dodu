package com.dodu.intervention;

import org.springframework.stereotype.Component;

/** Safe default until the external AI worker contract is configured. */
@Component
public final class UnconfiguredPhotoEvaluationRecoveryHandler implements PhotoEvaluationRecoveryHandler {
    @Override
    public RecoveryResult recover(RecoveryJob job) {
        return RecoveryResult.retryable("AI_WORKER_UNCONFIGURED");
    }
}
