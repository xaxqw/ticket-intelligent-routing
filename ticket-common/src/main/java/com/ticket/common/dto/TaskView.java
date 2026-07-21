package com.ticket.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 待办任务视图（Agent 模块可直接依赖的“公共”版本）。
 * 与 web 层的 {@code TaskVO} 字段对齐，但放在 common 以便 ticket-agent 在不依赖 web 模块的前提下使用，
 * 从而打破“agent 依赖 web、web 又依赖 agent”的循环依赖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskView {

    private String taskId;
    private String ticketId;
    private String title;
    private String nodeName;
    private String assignee;
    private LocalDateTime createTime;
}
