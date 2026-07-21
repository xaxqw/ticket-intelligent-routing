package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.dto.TicketVO;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/** 读工具：按 id 查询单个工单的详情与当前流转节点。 */
@Component
public class QueryTicketTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "query_ticket";
    }

    @Override
    public String description() {
        return "按工单 id 查询工单详情，包括分类、状态、处理人、当前流程节点与 SLA。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"description\":\"工单 id（数字串）\"}},"
                + "\"required\":[\"id\"]}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String id = Args.str(args, "id", "");
        if (id.isEmpty()) return ToolResult.builder().content("缺少工单 id").build();
        TicketVO t = ticketApi.getTicket(id);
        return ToolResult.builder().content(format(t)).build();
    }

    static String format(TicketVO t) {
        if (t == null) return "未找到该工单";
        StringBuilder sb = new StringBuilder();
        sb.append("工单 ").append(t.getId()).append("：").append(t.getTitle()).append("\n");
        sb.append("  分类=").append(t.getCategory() != null ? t.getCategory().getLabel() : "-")
                .append("；状态=").append(t.getStatus() != null ? t.getStatus().getLabel() : "-")
                .append("；优先级=").append(t.getPriority());
        sb.append("\n  处理人=").append(t.getAssignee() == null ? "(未分派)" : t.getAssignee());
        sb.append("；当前节点=").append(t.getCurrentNode() == null ? "-" : t.getCurrentNode());
        sb.append("\n  AI置信度=").append(t.getAiConfidence())
                .append("；待人工复核=").append(Boolean.TRUE.equals(t.getAiReviewRequired()));
        return sb.toString();
    }
}
