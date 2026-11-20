package com.dodu.intervention;

import java.util.UUID;

/** Adapter boundary for the external AI worker. It never receives raw photo bytes here. */
public interface PhotoEvaluationRecoveryHandler {
    RecoveryResult recover(RecoveryJob job);

    record RecoveryJob(UUID jobId, UUID attemptId, UUID promiseId, int attemptCount,
                       String storageKey, String contentType) { }

    /**
     * detectedLabel: AI가 본 물건(laptop/book/other). COMPLETED는 "허용 물건이 보였다"는 뜻일 뿐이며,
     * 약속 도구와의 일치 여부는 약속을 아는 worker가 최종 판단한다.
     */
    record RecoveryResult(Outcome outcome, String errorCode, String modelVersion, String detectedLabel) {
        public static RecoveryResult retryable(String errorCode) {
            return new RecoveryResult(Outcome.RETRYABLE_FAILURE, errorCode, null, null);
        }

        /** label 없는 완료. 도구 비교를 할 수 없으므로 운영 매퍼는 label 포함 판정을 사용한다. */
        public static RecoveryResult completed(String modelVersion) {
            return new RecoveryResult(Outcome.COMPLETED, null, modelVersion, null);
        }

        public static RecoveryResult completed(String detectedLabel, String modelVersion) {
            return new RecoveryResult(Outcome.COMPLETED, null, modelVersion, detectedLabel);
        }

        public static RecoveryResult rejected(String errorCode, String modelVersion) {
            return new RecoveryResult(Outcome.REJECTED, errorCode, modelVersion, null);
        }

        public static RecoveryResult technicalFailure(String errorCode) {
            return new RecoveryResult(Outcome.TECHNICAL_FAILURE, errorCode, null, null);
        }
    }

    enum Outcome { COMPLETED, REJECTED, RETRYABLE_FAILURE, TECHNICAL_FAILURE }
}
