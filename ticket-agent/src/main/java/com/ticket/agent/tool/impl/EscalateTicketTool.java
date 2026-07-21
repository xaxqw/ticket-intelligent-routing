package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.PendingAction;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 写工具：升级 / 动态驳回到指定流程节点（默认 audit2＝复审/上级审批）。
 * 复用了既有的“动态驳回”企业级能力（任意节点跳转）。未确认只提议。
 */
@Component
public class EscalateTicketTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "escalate_ticket";
    }

    @Override
    public String description() {
        return "将工单升级/上报到更高审批节点（默认复审 audit2）。会真正驱动流程跳转，需人工确认。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"ticketId\":{\"type\":\"string\",\"description\":\"工单 id\"},"
                + "\"targetActivityId\":{\"type\":\"string\",\"description\":\"目标节点：audit1/revise/pay/audit2，默认 audit2（复审/上级审批）\"}},"
                + "\"required\":[\"ticketId\"]}";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String ticketId = Args.str(args, "ticketId", "");
        String target = Args.str(args, "targetActivityId", "audit2");

        if (!confirmed) {
            String summary = "升级：将工单 " + ticketId + " 上报到节点 " + target + "（上级审批）";
            return ToolResult.builder()
                    .content("已生成升级操作，待人工确认。")
                    .pendingAction(new PendingAction(null, name(), summary, args))
                    .build();
        }

        ticketApi.rejectDynamic(ticketId, target, null);
        return ToolResult.builder()
                .content("已将工单 " + ticketId + " 升级到节点 " + target + "（上级审批）。").build();
    }
}
