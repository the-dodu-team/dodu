package com.dodu.common.exception;

import com.dodu.common.api.ApiErrorResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 업무 오류와 예상하지 못한 오류를 공통 오류 응답으로 바꿉니다.
 * 예상하지 못한 오류의 상세(스택, 메시지)는 응답에 넣지 않습니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.type().httpStatus())
                .body(ApiErrorResponse.of(e.type(), e.code(), e.field(), e.getMessage(), ApiTime.now(clock)));
    }

    /** 본문이 없거나 JSON 형식이 잘못된 요청. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ErrorType.VALIDATION, "REQUEST_INVALID", null,
                        "요청 형식이 올바르지 않아요.", ApiTime.now(clock)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        // 예외 객체를 그대로 남기면 요청 값이 섞일 수 있어 클래스 이름만 기록합니다.
        log.error("처리하지 못한 오류: {}", e.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(ErrorType.TECHNICAL, "INTERNAL_ERROR", null,
                        "일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", ApiTime.now(clock)));
    }
}