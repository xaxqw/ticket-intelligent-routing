package com.ticket.web.dto;

import lombok.Data;

/** 统一响应包装 */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> fail(String message) {
        ApiResponse<Void> r = new ApiResponse<>();
        r.code = 1;
        r.message = message;
        return r;
    }
}
