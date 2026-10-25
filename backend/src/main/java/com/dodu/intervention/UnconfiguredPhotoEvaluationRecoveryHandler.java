package com.dodu.intervention;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Safe default until the external AI worker contract is configured. */
@Component
@ConditionalOnProperty(name = "dodu.ai.enabled", havingValue = "false", matchIfMissing = true)
public final class UnconfiguredPhotoEvaluationRecoveryHandler implements PhotoEvaluationRecoveryHandler {
    @Override
    public RecoveryResult recover(RecoveryJob job) {
        return RecoveryResult.retryable("AI_WORKER_UNCONFIGURED");
    }
}
