package com.ticket.agent.tool.impl;

import com.ticket.ai.AiProperties;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.domain.Category;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/** 读工具：展示分类→候选组→置信度阈值的路由规则，让 Agent 的“为什么这么分派”可解释。 */
@Component
public class GetRoutingRulesTool implements Tool {

    @Resource
    private AiProperties aiProperties;

    @Override
    public String name() {
        return "get_routing_rules";
    }

    @Override
    public String description() {
        return "返回当前工单分类体系、各类对应的处理组（候选人组）以及触发人工复核的置信度阈值。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        StringBuilder sb = new StringBuilder();
        sb.append("分类与处理组映射：\n");
        for (Category c : Category.values()) {
            sb.append("  - ").append(c.getLabel()).append(" → 处理组 ")
                    .append(c.getCandidateGroup()).append("（").append(c.getDesc()).append("）\n");
        }
        sb.append("人工复核阈值：置信度 < ").append(aiProperties.getReviewThreshold())
                .append(" 时转人工复核（人在回路，避免误分派）。");
        return ToolResult.builder().content(sb.toString()).build();
    }
}
