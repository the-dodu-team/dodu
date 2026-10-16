package com.dodu.participant.domain;

/**
 * 세션이 종료된 사유({@code participant_session.revoke_reason}).
 *
 * <p>값은 응답 오류 코드와 이름을 맞춥니다. 코드 폐기·재발급, 철회, 접근 종료로 인한 종료 사유는
 * 해당 기능(운영 스크립트)을 만들 때 추가합니다.
 */
public enum SessionRevokeReason {
    /** 다른 기기에서 입장해 기존 세션이 종료됨. */
    SESSION_REPLACED;

    /** DB에 저장되는 문자열 값. */
    public String dbValue() {
        return name();
    }
}