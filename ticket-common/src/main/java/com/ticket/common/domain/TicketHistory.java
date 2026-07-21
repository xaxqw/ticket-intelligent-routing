package com.ticket.common.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单审计/流转历史。既满足企业工单合规刚需（谁、在何时、对哪个节点、做了什么），
 * 也为 SLA 仪表盘提供“各环节平均处理时长”的原始数据。
 */
@Entity
@Table(name = "t_ticket_history",
        indexes = {
                @Index(name = "idx_hist_ticket", columnList = "ticket_id"),
                @Index(name = "idx_hist_node", columnList = "node_name")
        })
@Getter
@Setter
@NoArgsConstructor
public class TicketHistory implements Serializable {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "ticket_id", length = 32, nullable = false)
    private String ticketId;

    /** 状态机视角：流转前/后状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 16)
    private TicketStatus toStatus;

    /** Flowable 视角：流程节点名（初审/复审/财务打款...） */
    @Column(name = "node_name", length = 32)
    private String nodeName;

    /** 操作人 */
    @Column(length = 64)
    private String operator;

    /** 动作：提交/接单/通过/驳回/挂起/恢复/归档 */
    @Column(length = 32)
    private String action;

    /** 驳回理由（仅驳回动作有值，用于合规审计与提交人查看） */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** 该节点耗时（毫秒），用于 SLA 聚合 */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TicketHistory(String id, String ticketId, String nodeName, String operator, String action) {
        this.id = id;
        this.ticketId = ticketId;
        this.nodeName = nodeName;
        this.operator = operator;
        this.action = action;
    }

    public TicketHistory(String id, String ticketId, String nodeName, String operator, String action, String reason) {
        this.id = id;
        this.ticketId = ticketId;
        this.nodeName = nodeName;
        this.operator = operator;
        this.action = action;
        this.reason = reason;
    }
}
