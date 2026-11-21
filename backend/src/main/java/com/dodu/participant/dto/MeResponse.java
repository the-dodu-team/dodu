package com.dodu.participant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 현재 참가자 상태 응답. 앱을 다시 열었을 때 어느 화면으로 갈지 정하는 데 씁니다.
 * 참가자 식별자·코드·세션 토큰은 담지 않습니다(계약 §4).
 *
 * @param onboardingCompleted false면 온보딩(S02-1), true면 홈(S03)
 * @param expiresAt 세션 만료 시각(+09:00). 접근 종료 시각과 같음
 * @param dailyPromiseLimit 하루(KST) 1약속 제한 적용 여부. 화면이 날짜 선택을 미리 막을지 정한다.
 *                          값이 없으면(온보딩 완료 응답 등) 응답에서 빠지고, 화면은 제한 적용으로 본다.
 */
// 이전: public record MeResponse(boolean onboardingCompleted, String expiresAt)
public record MeResponse(boolean onboardingCompleted, String expiresAt,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Boolean dailyPromiseLimit) {
    public MeResponse(boolean onboardingCompleted, String expiresAt) {
        this(onboardingCompleted, expiresAt, null);
    }
}
