package com.ticket.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 一次对话消息。role 决定其语义：
 *  - system/user/assistant：普通对话；
 *  - tool：工具执行结果回灌，需带 toolCallId 指明对应调用，name 为工具名。
 * assistant 消息若发起工具调用，则填 toolCalls。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    public enum Role { system, user, assistant, tool }

    private Role role;
    private String content;
    /** role=tool 时：被调用的工具名 */
    private String name;
    /** role=tool 时：对应 assistant 工具调用的 id */
    private String toolCallId;
    /** role=assistant 且发起工具调用时：本次要调用的工具列表 */
    private List<ToolCall> toolCalls;
}
