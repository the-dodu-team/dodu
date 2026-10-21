package com.dodu.participant.dto;

/**
 * 입장 응답.
 *
 * @param sessionToken 이후 요청의 {@code Authorization: Bearer} 값. 이 응답에서만 볼 수 있음
 * @param expiresAt 세션 만료 시각(+09:00)
 * @param onboardingCompleted false면 온보딩(S02-1), true면 홈(S03)으로 이동
 */
public record EnterResponse(String sessionToken, String expiresAt, boolean onboardingCompleted) {
}