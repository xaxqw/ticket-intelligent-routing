package com.ticket.agent.tool.impl;

import com.ticket.ai.TicketClassifier;
import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.dto.RouteResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/** 读工具：复用 AI 语义分派器，给出“这条工单属于哪类 + 置信度 + 相似证据”。 */
@Component
public class ClassifyIntentTool implements Tool {

    @Resource
    private TicketClassifier classifier;

    @Override
    public String name() {
        return "classify_intent";
    }

    @Override
    public String description() {
        return "判断一条工单描述应归属的业务分类（硬件类/财务类/权限类），并给出置信度与相似历史工单作为依据。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"title\":{\"type\":\"string\",\"description\":\"工单标题\"},"
                + "\"description\":{\"type\":\"string\",\"description\":\"工单描述\"}},"
                + "\"required\":[\"title\"]}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String title = Args.str(args, "title", "");
        String desc = Args.str(args, "description", "");
        RouteResult r = classifier.classify(title, desc);

        StringBuilder sb = new StringBuilder();
        sb.append("分类结果：").append(r.getCategory() != null ? r.getCategory().getLabel() : "未知");
        sb.append("；置信度：").append(r.getConfidence());
        sb.append("；").append(r.isReviewRequired() ? "置信度偏低，建议人工复核" : "可自动分派");
        if (r.getMatches() != null && !r.getMatches().isEmpty()) {
            sb.append("。相似工单：");
            for (RouteResult.MatchedTicket m : r.getMatches()) {
                sb.append("\n  - [").append(m.getScore()).append("] ")
                        .append(m.getText()).append(" → ").append(m.getCategory() != null ? m.getCategory().getLabel() : "?");
            }
        }
        return ToolResult.builder().content(sb.toString()).build();
    }
}
