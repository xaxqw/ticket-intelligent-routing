package com.ticket.common.dto;

import com.ticket.common.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * AI 语义分派结果。返回最优分类 + 置信度 + Top-K 相似历史工单（few-shot 证据），
 * 让“为什么分到这一类”可解释，便于人工确认。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {

    private Category category;
    private double confidence;

    /** 置信度低于阈值时为 true，需转人工复核（人在回路） */
    private boolean reviewRequired;

    /** 触发人工复核的置信度阈值（便于前端展示与复现） */
    private double reviewThreshold;

    /** Top-K 相似历史工单，作为分类依据展示 */
    @Builder.Default
    private List<MatchedTicket> matches = Collections.emptyList();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MatchedTicket {
        private String text;
        private double score;
        private Category category;
    }
}
