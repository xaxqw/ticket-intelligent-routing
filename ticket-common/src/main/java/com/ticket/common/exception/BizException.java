package com.ticket.common.exception;

import lombok.Getter;

/**
 * 业务异常。统一携带错误码，便于前端与日志定位。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this("BIZ_ERROR", message);
    }
}
