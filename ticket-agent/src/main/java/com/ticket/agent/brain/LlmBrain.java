package com.ticket.agent.brain;

import com.ticket.agent.llm.LlmClient;
import com.ticket.agent.llm.LlmMessage;
import com.ticket.agent.llm.LlmResponse;
import com.ticket.agent.llm.ToolCall;
import com.ticket.agent.llm.ToolSpec;

import java.util.List;
import java.util.UUID;

/**
 * 基于真实大模型的“大脑”：把对话历史与工具规格交给 {@link LlmClient}（OpenAI 兼容接口），
 * 由模型自行决定调用哪个工具、何时收尾。这是 Agent 真正“智能”的来源。
 */
public class LlmBrain implements AgentBrain {

    private final LlmClient client;

    public LlmBrain(LlmClient client) {
        this.client = client;
    }

    @Override
    public BrainDecision decide(List<LlmMessage> history, List<ToolSpec> tools) {
        LlmResponse resp = client.chat(history, tools);
        if (resp.getToolCalls() != null && !resp.getToolCalls().isEmpty()) {
            // 模型可能一次返回多个工具调用；按顺序执行第一个，其余留待下一轮
            return BrainDecision.callTool(resp.getToolCalls().get(0));
        }
        return BrainDecision.finish(resp.getContent() == null ? "" : resp.getContent());
    }
}
