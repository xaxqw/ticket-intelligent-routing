package com.ticket.common.exception;

/**
 * 幂等冲突：同一 idempotencyKey 已存在对应工单，重试不应再建单。
 */
public class IdempotencyException extends BizException {

    private final String existingTicketId;

    public IdempotencyException(String existingTicketId) {
        super("IDEMPOTENT_DUPLICATE", "该请求已处理，返回原工单");
        this.existingTicketId = existingTicketId;
    }

    public String getExistingTicketId() {
        return existingTicketId;
    }
}
