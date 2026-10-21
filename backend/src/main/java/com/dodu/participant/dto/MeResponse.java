package com.dodu.participant.dto;

/**
 * 현재 참가자 상태 응답. 앱을 다시 열었을 때 어느 화면으로 갈지 정하는 데 씁니다.
 * 참가자 식별자·코드·세션 토큰은 담지 않습니다(계약 §4).
 *
 * @param onboardingCompleted false면 온보딩(S02-1), true면 홈(S03)
 * @param expiresAt 세션 만료 시각(+09:00). 접근 종료 시각과 같음
 */
public record MeResponse(boolean onboardingCompleted, String expiresAt) {
}