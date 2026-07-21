package com.ticket.ai;

import com.ticket.common.domain.Category;
import com.ticket.common.dto.RouteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AI 语义分派器。核心思路（极简有效、不训练模型）：
 *  1. 历史工单已向量化入库；
 *  2. 新工单描述 → 向量 → 余弦检索 Top-K 相似历史工单（few-shot）；
 *  3. 按相似度加权投票得出分类，并给出可解释的 Top-K 证据与置信度。
 *
 * 不做端到端训练，工程成本低、可解释强、上线快——这正是企业场景的务实选择。
 */
public class TicketClassifier {

    private final VectorStore store;
    private final int topK;
    private double reviewThreshold = 0.55;

    public TicketClassifier(VectorStore store, int topK) {
        this.store = store;
        this.topK = topK;
    }

    public void setReviewThreshold(double reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    /** 将历史工单灌入向量库（启动时调用一次） */
    public void seed(List<HistoricalTicket> history) {
        for (HistoricalTicket h : history) {
            Category c = Category.fromLabel(h.getCategory());
            if (c != null) store.upsert(h.getId(), h.getText(), c);
        }
    }

    /** 在线学习：人工最终结论回流向量库，使同类工单下次更易被正确分派 */
    public void learn(String text, Category category) {
        store.upsert("learned-" + System.nanoTime(), text, category);
    }

    public RouteResult classify(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description)).trim();
        List<VectorStore.ScoredItem> top = store.search(text, topK);

        if (top.isEmpty()) {
            // 库空时的兜底：基于关键词启发式，保证永远有结果
            return RouteResult.builder()
                    .category(heuristic(text))
                    .confidence(0.0)
                    .matches(Collections.emptyList())
                    .build();
        }

        // 按分类聚合相似度
        Map<Category, Double> agg = new EnumMap<>(Category.class);
        double total = 0.0;
        for (VectorStore.ScoredItem s : top) {
            double w = Math.max(0, s.score);
            agg.merge(s.category, w, Double::sum);
            total += w;
        }
        Category best = null;
        double bestSum = -1;
        for (Map.Entry<Category, Double> e : agg.entrySet()) {
            if (e.getValue() > bestSum) {
                bestSum = e.getValue();
                best = e.getKey();
            }
        }
        double confidence = total > 0 ? bestSum / total : 0.0;
        // 综合置信度 = 投票占比 × 相似度强度。
        // 仅用投票占比会在“少数词命中单一类”时虚高到 1.0（如泛词“处理”偏财务），
        // 乘上相似度强度可抑制过自信，让低信号输入正确转人工复核（人在回路）。
        double topSim = top.isEmpty() ? 0.0 : top.get(0).score;
        double simFactor = Math.min(1.0, topSim / 0.35);
        confidence = confidence * simFactor;
        // 置信度低于阈值视为“不确定”，交由人工复核（人在回路，避免误分派）
        boolean reviewRequired = confidence < reviewThreshold;

        List<RouteResult.MatchedTicket> matches = new ArrayList<>();
        for (VectorStore.ScoredItem s : top) {
            if (s.category == null) continue;
            matches.add(new RouteResult.MatchedTicket(s.text, round(s.score), s.category));
        }
        return RouteResult.builder()
                .category(best)
                .confidence(round(confidence))
                .reviewRequired(reviewRequired)
                .reviewThreshold(reviewThreshold)
                .matches(matches)
                .build();
    }

    private Category heuristic(String text) {
        if (text.matches(".*(报销|发票|付款|打款|预算|费用).*")) return Category.FINANCE;
        if (text.matches(".*(账号|权限|授权|开通|登录|密码).*")) return Category.PERMISSION;
        return Category.HARDWARE;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
