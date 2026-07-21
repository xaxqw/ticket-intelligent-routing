package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.PendingAction;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/** 写工具：接单 / 分派（认领任务给某处理人）。未确认只提议。 */
@Component
public class AssignTicketTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "assign_ticket";
    }

    @Override
    public String description() {
        return "将某条工单的待办任务认领并分派给指定处理人。会真正修改任务归属，需人工确认。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"ticketId\":{\"type\":\"string\",\"description\":\"工单 id\"},"
                + "\"taskId\":{\"type\":\"string\",\"description\":\"待办任务 id（可由 list_my_tasks 获取）\"},"
                + "\"userId\":{\"type\":\"string\",\"description\":\"分派给的处理人\"}},"
                + "\"required\":[\"ticketId\",\"taskId\",\"userId\"]}";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String ticketId = Args.str(args, "ticketId", "");
        String taskId = Args.str(args, "taskId", "");
        String userId = Args.str(args, "userId", "");

        if (!confirmed) {
            String summary = "分派：将工单 " + ticketId + " 的任务 " + taskId + " 交给 " + userId;
            return ToolResult.builder()
                    .content("已生成分派操作，待人工确认。")
                    .pendingAction(new PendingAction(null, name(), summary, args))
                    .build();
        }

        ticketApi.claim(ticketId, taskId, userId);
        return ToolResult.builder()
                .content("已将工单 " + ticketId + " 的任务 " + taskId + " 分派给 " + userId + "。").build();
    }
}
