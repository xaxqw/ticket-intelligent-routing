package com.ticket.web.dto;

import lombok.Data;

/** SLA 节点统计：某环节的平均/最大处理时长，以及是否超阈值（慢节点） */
@Data
public class SlaNodeStat {
    private String nodeName;
    private long avgDurationMs;
    private long maxDurationMs;
    private int count;
    private boolean slow;
}
