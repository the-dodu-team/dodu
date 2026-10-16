package com.dodu.common.api;

import com.dodu.common.exception.ErrorType;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 오류 응답: {@code {"status":"error","error":{...},"serverTime":"..."}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String status, ErrorBody error, String serverTime) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String type, String code, String field, String message) {
    }

    public static ApiErrorResponse of(ErrorType type, String code, String field, String message, String serverTime) {
        return new ApiErrorResponse("error", new ErrorBody(type.name(), code, field, message), serverTime);
    }
}