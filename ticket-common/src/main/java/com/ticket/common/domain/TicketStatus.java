package com.ticket.common.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单状态机。
 * 严格定义状态与合法流转，所有状态变更必须经过 {@link #canTransitionTo(TicketStatus)} 校验，
 * 杜绝“状态混乱”这一核心痛点。
 *
 * 流转图：
 *   待接单(PENDING) ──接单──▶ 处理中(PROCESSING)
 *   处理中(PROCESSING) ──挂起──▶ 挂起(SUSPENDED)
 *   挂起(SUSPENDED) ──恢复──▶ 处理中(PROCESSING)
 *   处理中(PROCESSING) ──归档──▶ 已完成(COMPLETED)
 *   已完成(COMPLETED) ──重开──▶ 处理中(PROCESSING)
 *   任意活跃态 ──取消──▶ 已取消(CANCELLED)
 */
public enum TicketStatus {

    PENDING(1, "待接单", EnumSet.of(StatusTag.PROCESSING, StatusTag.CANCELLED)),
    PROCESSING(2, "处理中", EnumSet.of(StatusTag.SUSPENDED, StatusTag.COMPLETED, StatusTag.CANCELLED)),
    SUSPENDED(3, "挂起", EnumSet.of(StatusTag.PROCESSING, StatusTag.CANCELLED)),
    COMPLETED(4, "已完成", EnumSet.of(StatusTag.PROCESSING, StatusTag.CANCELLED)),
    CANCELLED(5, "已取消", EnumSet.noneOf(StatusTag.class));

    private final int code;
    private final String label;
    /** 允许到达的目标状态集合（用语义标签表示，避免与枚举顺序耦合） */
    private final Set<StatusTag> nextTags;

    TicketStatus(int code, String label, Set<StatusTag> nextTags) {
        this.code = code;
        this.label = label;
        this.nextTags = nextTags;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitionTo(TicketStatus target) {
        if (target == null) return false;
        return nextTags.contains(StatusTag.fromStatus(target));
    }

    /** 校验流转合法性，非法则抛异常（由调用方捕获后转业务异常） */
    public void assertTransition(TicketStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("非法状态流转: %s(%s) -> %s(%s)",
                            this, this.label, target, target.label));
        }
    }

    public static TicketStatus ofCode(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工单状态码: " + code));
    }

    /** 状态语义标签，用于解耦“允许去向”与枚举声明顺序 */
    public enum StatusTag {
        PENDING, PROCESSING, SUSPENDED, COMPLETED, CANCELLED;

        static StatusTag fromStatus(TicketStatus s) {
            return StatusTag.valueOf(s.name());
        }
    }
}
