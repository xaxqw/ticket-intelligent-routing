package com.ticket.common.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单主表。状态、分类、幂等键、流程实例 id 均落库，
 * 与 Flowable 的运行时数据通过 {@link #processInstanceId} 关联。
 */
@Entity
@Table(name = "t_ticket",
        indexes = {
                @Index(name = "idx_ticket_idem", columnList = "idempotency_key", unique = true),
                @Index(name = "idx_ticket_status", columnList = "status"),
                @Index(name = "idx_ticket_category", columnList = "category")
        })
@Getter
@Setter
@NoArgsConstructor
public class Ticket implements Serializable {

    @Id
    @Column(length = 32)
    private String id;

    /** 幂等键：客户端提交时生成，重试不致重复建单 */
    @Column(name = "idempotency_key", length = 64, unique = true, nullable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Category category;

    /** AI 分派置信度（0~1），仅供人工参考 */
    @Column(name = "ai_confidence")
    private Double aiConfidence;

    /** AI 置信度低于阈值，需人工复核（人在回路）；复核前不启动审批流程 */
    @Column(name = "ai_review_required")
    private Boolean aiReviewRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private TicketStatus status;

    /** 优先级：1 低 2 中 3 高 */
    @Column(nullable = false)
    private Integer priority = 2;

    /** Flowable 流程实例 id */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /** 当前处理人 */
    @Column(length = 64)
    private String assignee;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** SLA 截止时间 */
    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 最近一次驳回理由（工单停在补充材料节点时展示给提交人，便于针对性补材料） */
    @Column(name = "last_reject_reason", columnDefinition = "TEXT")
    private String lastRejectReason;

    @Version
    private Long version;

    public Ticket(String id, String idempotencyKey, String title, String description) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.title = title;
        this.description = description;
        this.status = TicketStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
