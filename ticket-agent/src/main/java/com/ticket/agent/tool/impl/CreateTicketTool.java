package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.PendingAction;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.dto.CreateTicketRequest;
import com.ticket.common.dto.TicketVO;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;

/** 写工具：创建工单（含 AI 语义分派 / 人在回路）。未确认只提议，确认才真正落库并启动流程。 */
@Component
public class CreateTicketTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "create_ticket";
    }

    @Override
    public String description() {
        return "创建一条新工单。系统会自动做 AI 语义分派；若置信度低则转人工复核。会真正写入数据库并启动审批流程。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"title\":{\"type\":\"string\",\"description\":\"工单标题\"},"
                + "\"description\":{\"type\":\"string\",\"description\":\"工单描述\"},"
                + "\"priority\":{\"type\":\"integer\",\"description\":\"优先级 1低 2中 3高，默认2\"},"
                + "\"category\":{\"type\":\"string\",\"description\":\"可选：人工指定分类（硬件类/财务类/权限类），缺省由AI分派\"},"
                + "\"slaHours\":{\"type\":\"integer\",\"description\":\"SLA时长(小时)，默认24\"}},"
                + "\"required\":[\"title\"]}";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String title = Args.str(args, "title", "");
        String desc = Args.str(args, "description", "");
        int priority = Args.integer(args, "priority", 2);
        int sla = Args.integer(args, "slaHours", 24);
        String category = Args.str(args, "category", "");

        if (!confirmed) {
            String summary = "建单：" + (category.isEmpty() ? "（AI自动分派）" : category + "类")
                    + "『" + title + "』（优先级" + priority + "，SLA " + sla + "h）";
            return ToolResult.builder()
                    .content("已生成建单操作，待人工确认。")
                    .pendingAction(new PendingAction(null, name(), summary, args))
                    .build();
        }

        CreateTicketRequest req = CreateTicketRequest.builder()
                .title(title)
                .description(desc)
                .priority(priority)
                .slaHours(sla)
                .category(category.isEmpty() ? null : category)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        TicketVO vo = ticketApi.createTicket(req);
        return ToolResult.builder()
                .content("已创建工单：\n" + QueryTicketTool.format(vo)).build();
    }
}
