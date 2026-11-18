package com.dodu.promise;

import com.dodu.common.api.ApiErrorResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.common.exception.ErrorType;
import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 예상 가능한 약속 검증 실패를 일반 서버 장애로 처리하지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PromiseController.class)
public class PromiseValidationExceptionHandler {
    private final Clock clock;

    public PromiseValidationExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(PromiseRequestValidator.PromiseValidationException.class)
    public ResponseEntity<ApiErrorResponse> handle(PromiseRequestValidator.PromiseValidationException error) {
        // 이전: GlobalExceptionHandler의 일반 Exception 경로에서 500 INTERNAL_ERROR로 처리.
        // 검증 조건과 트랜잭션은 그대로 유지하고 허용된 코드·안내만 반환한다.
        String code = switch (error.getMessage()) {
            case "INVALID_TASK_TITLE", "INVALID_TOOL_TYPE", "DATETIME_OFFSET_REQUIRED",
                    "PROMISE_SCHEDULE_TOO_SOON", "PROMISE_SCHEDULE_TOO_FAR",
                    "PROMISE_SCHEDULE_AFTER_STUDY_END" -> error.getMessage();
            default -> "REQUEST_INVALID";
        };
        String message = switch (code) {
            case "PROMISE_SCHEDULE_AFTER_STUDY_END" -> "참여 기간 안의 시작 시간을 선택해 주세요.";
            case "PROMISE_SCHEDULE_TOO_SOON" -> "현재 시각부터 최소 5분 뒤의 시작 시간을 선택해 주세요.";
            case "PROMISE_SCHEDULE_TOO_FAR" -> "현재 시각부터 72시간 이내의 시작 시간을 선택해 주세요.";
            case "INVALID_TASK_TITLE" -> "작업명을 1자 이상 30자 이내로 입력해 주세요.";
            case "INVALID_TOOL_TYPE" -> "인증 도구를 다시 선택해 주세요.";
            default -> "요청 형식을 확인해 주세요.";
        };
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                ErrorType.VALIDATION, code, null, message, ApiTime.now(clock)));
    }
}
