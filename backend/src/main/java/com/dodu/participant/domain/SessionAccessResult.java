package com.dodu.participant.domain;

/** 세션으로 온 요청의 접근 판정 결과({@link SessionContext#check}). */
public enum SessionAccessResult {
    /** 접근 허용. */
    ALLOWED,
    /** 세션 시간이 지남. 코드를 다시 입력해 입장해야 함 → {@code ACCESS_REQUIRED}. */
    EXPIRED,
    /** 다른 기기 입장으로 종료됨 → {@code SESSION_REPLACED}. */
    REPLACED,
    /** 코드 폐기·재발급, 철회, 접근 종료로 접근할 수 없음 → {@code ACCESS_REVOKED}. */
    REVOKED
}