package com.ticket.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 待办任务视图（前端“我的待办/组内待办”使用） */
@Data
public class TaskVO {
    private String taskId;
    private String ticketId;
    private String title;
    private String nodeName;
    private String assignee;
    private LocalDateTime createTime;

    /** 最近一次驳回理由（来自所属工单，停在补充材料时展示给提交人） */
    private String rejectReason;
}
