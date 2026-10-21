package com.dodu.common.exception;

import com.dodu.common.api.ApiErrorResponse;
import com.dodu.common.api.ApiTime;
import java.time.Clock;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 업무 오류와 예상하지 못한 오류를 공통 오류 응답으로 바꿉니다.
 * 예상하지 못한 오류의 상세(스택, 메시지)는 응답에 넣지 않습니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException e) {
        int status = e.type().httpStatus();
        if (status >= 500) {
            log.error("업무 처리 중 서버 오류: {}", e.code(), e);
        }
        return ResponseEntity.status(e.type().httpStatus())
                .body(ApiErrorResponse.of(e.type(), e.code(), e.field(), e.getMessage(), ApiTime.now(clock)));
    }

    /**
     * 본문이 없거나 JSON 형식이 잘못된 요청.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ErrorType.VALIDATION, "REQUEST_INVALID", null,
                        "요청 형식이 올바르지 않아요.", ApiTime.now(clock)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse errorResponse && !errorResponse.getStatusCode().is5xxServerError()) {
            return handleClientError(errorResponse);
        }
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(ErrorType.TECHNICAL, "INTERNAL_ERROR", null,
                        "일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", ApiTime.now(clock)));
    }

    /**
     * 스프링 MVC와 ResponseStatusException이 정한 4xx 상태를 그대로 유지합니다.
     */
    private ResponseEntity<ApiErrorResponse> handleClientError(ErrorResponse e) {
        HttpStatusCode status = e.getStatusCode();
        String reason = e instanceof ResponseStatusException rse ? rse.getReason() : null;
        // 사유가 오류 코드 형식(대문자_스네이크)일 때만 코드로 쓰고, 자유 문장은 응답에 노출하지 않습니다.
        String code = reason != null && CODE_FORMAT.matcher(reason).matches() ? reason : defaultCode(status);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(typeOf(status), code, null, messageOf(status), ApiTime.now(clock)));
    }

    private static ErrorType typeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorType.UNAUTHENTICATED;
            case 403 -> ErrorType.FORBIDDEN;
            case 404 -> ErrorType.NOT_FOUND;
            case 409 -> ErrorType.CONFLICT;
            default -> ErrorType.VALIDATION;
        };
    }

    private static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "ACCESS_REQUIRED";
            case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> "REQUEST_INVALID";
        };
    }

    private static String messageOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "인증이 필요해요.";
            case 403 -> "접근할 수 없어요.";
            case 404 -> "찾을 수 없어요.";
            case 409 -> "요청을 처리할 수 없는 상태예요.";
            default -> "요청이 올바르지 않아요.";
        };

    }
}