package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import com.ticket.common.dto.TaskView;
import com.ticket.common.spi.TicketCommandApi;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 读工具：查询待办任务（Flowable 任务）。输出采用可被规划器解析的格式
 * （每行 {@code taskId=...; ticketId=...; ...}），便于“派单”等多步流程自动取用 taskId。
 */
@Component
public class ListTasksTool implements Tool {

    @Resource
    private TicketCommandApi ticketApi;

    @Override
    public String name() {
        return "list_my_tasks";
    }

    @Override
    public String description() {
        return "查询当前待办任务（Flowable 任务），可按处理人或候选组过滤。用于定位“派单”所需的 taskId。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"group\":{\"type\":\"string\",\"description\":\"候选组过滤：hardware-group/finance-group/permission-group\"},"
                + "\"assignee\":{\"type\":\"string\",\"description\":\"处理人过滤\"}}}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String group = Args.str(args, "group", "");
        String assignee = Args.str(args, "assignee", "");
        List<TaskView> list = ticketApi.listTaskViews(
                group.isEmpty() ? null : group,
                assignee.isEmpty() ? null : assignee);
        if (list == null || list.isEmpty()) {
            return ToolResult.builder().content("待办任务(共0条)").build();
        }
        StringBuilder sb = new StringBuilder("待办任务(共").append(list.size()).append("条):\n");
        for (TaskView t : list) {
            sb.append("  ").append(list.indexOf(t) + 1).append(") taskId=").append(t.getTaskId())
                    .append("; ticketId=").append(t.getTicketId())
                    .append("; title=").append(t.getTitle())
                    .append("; node=").append(t.getNodeName())
                    .append("; assignee=").append(t.getAssignee() == null ? "(未认领)" : t.getAssignee())
                    .append("\n");
        }
        return ToolResult.builder().content(sb.toString()).build();
    }
}
