package com.dodu.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 성공 응답: {@code {"status":"success","data":{...},"serverTime":"..."}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, T data, String serverTime) {

    public static <T> ApiResponse<T> success(T data, String serverTime) {
        return new ApiResponse<>("success", data, serverTime);
    }
}