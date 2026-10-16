package com.dodu.participant.dto;

/**
 * 입장 요청.
 *
 * @param code 참가자 입력 코드(구분자·대소문자 무관)
 * @param deviceKey 브라우저가 만들어 {@code localStorage}/IndexedDB에 보관하는 무작위 기기 식별 키
 */
public record EnterRequest(String code, String deviceKey) {
}