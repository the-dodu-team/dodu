package com.dodu.intervention;

import java.util.UUID;

/** 사진 제출이 판정 작업을 남겼다는 신호. 사진·접근정보는 담지 않는다. */
public record PhotoEvaluationRequested(UUID attemptId) { }
