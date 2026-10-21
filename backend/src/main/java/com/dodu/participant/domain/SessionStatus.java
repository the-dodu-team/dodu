package com.dodu.participant.domain;

/**
 * {@code participant_session.lifecycle_status} 값. V1에서 미확정(OPEN)이던 값을 SCRUM-22에서 정했습니다
 * (개발 결정). {@code Active}는 DB 유니크 인덱스(참가자당 활성 세션 1개)가 쓰는 값이라 바꾸지 않습니다.
 */
public enum SessionStatus {
    ACTIVE("Active"),
    ENDED("Ended");

    private final String dbValue;

    SessionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** DB에 저장되는 문자열 값. */
    public String dbValue() {
        return dbValue;
    }
}