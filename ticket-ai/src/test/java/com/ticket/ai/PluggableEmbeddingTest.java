package com.ticket.ai;

import com.ticket.common.domain.Category;
import com.ticket.common.dto.RouteResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证“可插拔向量化”：{@link TicketClassifier} + {@link LocalVectorStore} 不依赖
 * 具体向量器实现，只要传入 {@code Function<String, Map<String,Double>>} 即可工作。
 *
 * 这里用一个确定性的“稠密 embedding 桩”（把类别关键词投影到 3 维空间，模拟真实
 * 语义 embedding 的聚类效果）替代 TF-IDF，证明切换到远程稠密 embedding 时分类器
 * 行为一致、无需改动——这正是生产可一键切换 BGE / OpenAI embedding 的架构基础。
 */
public class PluggableEmbeddingTest {

    /** 桩稠密向量化：按类别关键词把文本投影到固定维度并 L2 归一化 */
    static Map<String, Double> stubEmbed(String text) {
        double[] base = new double[3];
        if (text.contains("电脑") || text.contains("投影仪") || text.contains("网络") || text.contains("蓝屏")) base[0] += 1;
        if (text.contains("报销") || text.contains("发票") || text.contains("付款") || text.contains("打车")) base[1] += 1;
        if (text.contains("账号") || text.contains("权限") || text.contains("密码") || text.contains("开通")) base[2] += 1;
        double norm = 0;
        for (double x : base) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) { base[0] = 1; norm = 1; }
        Map<String, Double> v = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) v.put(String.valueOf(i), base[i] / norm);
        return v;
    }

    private static HistoricalTicket ticket(String id, String text, String category) {
        HistoricalTicket h = new HistoricalTicket();
        h.setId(id);
        h.setText(text);
        h.setCategory(category);
        return h;
    }

    @Test
    void classifierWorksWithDenseEmbeddingVectorizer() {
        LocalVectorStore store = new LocalVectorStore(PluggableEmbeddingTest::stubEmbed);
        TicketClassifier classifier = new TicketClassifier(store, 5);
        classifier.seed(Arrays.asList(
                ticket("h1", "笔记本电脑无法开机", "硬件类"),
                ticket("h2", "投影仪连不上会议", "硬件类"),
                ticket("f1", "差旅报销发票", "财务类"),
                ticket("p1", "账号权限申请", "权限类")
        ));

        RouteResult hardware = classifier.classify("台式机蓝屏了", "");
        assertEquals(Category.HARDWARE, hardware.getCategory(), "稠密向量器下硬件类应正确分派");

        RouteResult finance = classifier.classify("请帮我报销打车费", "");
        assertEquals(Category.FINANCE, finance.getCategory(), "稠密向量器下财务类应正确分派");

        RouteResult permission = classifier.classify("开通系统登录密码", "");
        assertEquals(Category.PERMISSION, permission.getCategory(), "稠密向量器下权限类应正确分派");
    }
}
