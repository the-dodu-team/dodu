package com.dodu.common.exception;

/** 오류 유형과 HTTP 상태. docs/api/COMMON_API_CONTRACT.md 3.3 표를 따릅니다. */
public enum ErrorType {
    VALIDATION(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    TECHNICAL(500);

    private final int httpStatus;

    ErrorType(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}