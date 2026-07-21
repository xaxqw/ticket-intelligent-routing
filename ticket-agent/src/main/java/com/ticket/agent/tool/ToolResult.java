package com.ticket.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 工具执行结果。写工具未确认时 content 为提示、pendingAction 非空。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private String content;
    private PendingAction pendingAction;
}
