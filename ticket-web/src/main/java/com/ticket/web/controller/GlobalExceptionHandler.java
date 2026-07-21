package com.ticket.web.controller;

import com.ticket.common.exception.BizException;
import com.ticket.common.exception.IdempotencyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一异常处理。幂等冲突返回 200（视为“已处理，返回原工单”），
 * 其余业务异常返回 400 + 错误码，便于前端与日志定位。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotent(IdempotencyException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("ticketId", ex.getExistingTicketId());
        body.put("duplicate", true);
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "ILLEGAL_STATE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
