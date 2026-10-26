package com.dodu.common.exception;

import java.util.Objects;

/**
 * 예상 가능한 업무 오류. {@link com.dodu.common.exception.GlobalExceptionHandler}가 공통 오류 응답으로 바꿉니다.
 * 메시지에는 사용자에게 보여도 안전한 문구만 넣습니다(코드·토큰·내부 정보 금지).
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorType type;
    private final String code;
    private final String field;

    public BusinessException(ErrorType type, String code, String message) {
        this(type, code, null, message);
    }

    public BusinessException(ErrorType type, String code, String field, String message) {
        super(message);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.field = field;
    }

    public ErrorType type() {
        return type;
    }

    public String code() {
        return code;
    }

    /** 입력 오류에서 문제가 된 항목. 없으면 null. */
    public String field() {
        return field;
    }
}