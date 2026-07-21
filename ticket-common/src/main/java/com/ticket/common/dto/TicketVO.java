package com.ticket.common.dto;

import com.ticket.common.domain.Category;
import com.ticket.common.domain.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 工单视图对象，返回给前端/调用方 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketVO {

    private String id;
    private String title;
    private String description;
    private Category category;
    private Double aiConfidence;

    /** 是否待 AI 人工复核（置信度低于阈值） */
    private Boolean aiReviewRequired;
    private TicketStatus status;
    private Integer priority;
    private String assignee;
    private String processInstanceId;

    /** 最近一次驳回理由（停在补充材料时展示给提交人） */
    private String lastRejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime slaDueAt;
    private LocalDateTime completedAt;

    /** 当前所处流程节点（来自 Flowable），便于前端展示“流转到哪了” */
    private String currentNode;
}
