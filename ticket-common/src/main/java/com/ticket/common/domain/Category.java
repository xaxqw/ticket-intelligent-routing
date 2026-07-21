package com.ticket.common.domain;

import lombok.Getter;

/**
 * 工单业务分类。AI 语义分派的目标类别，也是 Flowable 流程中
 * 候选人组（candidate group）与路由策略的依据。
 */
@Getter
public enum Category {

    HARDWARE("硬件类", "hardware-group", "硬件/网络/设备报修"),
    FINANCE("财务类", "finance-group", "报销/付款/发票/打款"),
    PERMISSION("权限类", "permission-group", "账号/权限/授权/账号开通");

    private final String label;
    /** Flowable 中对应的候选人组 id */
    private final String candidateGroup;
    private final String desc;

    Category(String label, String candidateGroup, String desc) {
        this.label = label;
        this.candidateGroup = candidateGroup;
        this.desc = desc;
    }

    public static Category fromLabel(String label) {
        for (Category c : values()) {
            if (c.label.equals(label)) return c;
        }
        return null;
    }
}
