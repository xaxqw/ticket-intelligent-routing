package com.ticket.agent.brain;

import com.ticket.agent.llm.ToolCall;

/**
 * Agent 的“大脑”抽象。给定当前对话历史与可用工具，决定下一步：
 *  - 调用某个工具（{@link Type#CALL_TOOL}），或
 *  - 给出最终答复、结束本轮（{@link Type#FINISH}）。
 *
 * 这是替换“智能来源”的扩展点：{@code LlmBrain} 接真实大模型，{@code RuleBrain} 是无需 Key 的本地确定性规划器。
 */
public interface AgentBrain {

    BrainDecision decide(java.util.List<com.ticket.agent.llm.LlmMessage> history,
                         java.util.List<com.ticket.agent.llm.ToolSpec> tools);

    enum Mode { LLM, RULE }
}
