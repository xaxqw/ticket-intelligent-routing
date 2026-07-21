package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.dto.TicketVO;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/** 读工具：列表查询工单（可按状态/分类/是否待复核过滤）。 */
@Component
public class ListTicketsTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "list_tickets";
    }

    @Override
    public String description() {
        return "列出工单列表，可按状态、分类过滤；review=true 时仅列“低置信、待人工复核”的工单。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"status\":{\"type\":\"string\",\"description\":\"状态过滤：PENDING/PROCESSING/SUSPENDED/COMPLETED/CANCELLED\"},"
                + "\"category\":{\"type\":\"string\",\"description\":\"分类过滤：硬件类/财务类/权限类\"},"
                + "\"review\":{\"type\":\"boolean\",\"description\":\"true 时仅列待人工复核的工单\"}}}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String status = Args.str(args, "status", "");
        String category = Args.str(args, "category", "");
        Boolean review = Args.bool(args, "review", false) ? Boolean.TRUE : null;
        List<TicketVO> list = ticketApi.listTickets(
                status.isEmpty() ? null : status,
                category.isEmpty() ? null : category,
                review);
        if (list == null || list.isEmpty()) {
            return ToolResult.builder().content("没有符合条件的工单").build();
        }
        StringBuilder sb = new StringBuilder("共 ").append(list.size()).append(" 条工单：\n");
        for (TicketVO t : list) {
            sb.append("  - ").append(t.getId()).append(" | ")
                    .append(t.getTitle()).append(" | ")
                    .append(t.getCategory() != null ? t.getCategory().getLabel() : "-").append(" | ")
                    .append(t.getStatus() != null ? t.getStatus().getLabel() : "-").append("\n");
        }
        return ToolResult.builder().content(sb.toString()).build();
    }
}
