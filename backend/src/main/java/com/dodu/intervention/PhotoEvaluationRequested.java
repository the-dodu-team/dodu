package com.dodu.intervention;

import java.util.UUID;

/**
 * 사진 제출이 판정 작업을 남겼다는 신호. 접근정보는 담지 않는다.
 *
 * <p>{@code photo}: 방금 저장소에 올린 그 사진(같은 요청의 바이트). 즉시 판정 경로가 저장소에서 다시 내려받지 않도록
 * 메모리에서 넘긴다. 없으면(기존 attempt 재요청 등) 처리 쪽이 저장소에서 읽는다.
 * 이전: attemptId만 담았다("사진·접근정보는 담지 않는다"). 사진 내용은 로그·toString에 나가지 않게 한다.
 */
public record PhotoEvaluationRequested(UUID attemptId, PhotoObjectReader.PhotoObject photo) {
    public PhotoEvaluationRequested(UUID attemptId) {
        this(attemptId, null);
    }

    @Override
    public String toString() {
        return "PhotoEvaluationRequested[attemptId=" + attemptId + ", inlinePhoto=" + (photo != null) + "]";
    }
}
