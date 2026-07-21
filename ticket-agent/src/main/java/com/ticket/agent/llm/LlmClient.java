package com.ticket.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端抽象。实现可插拔：
 *  - {@code OpenAiCompatibleLlmClient}：调用 OpenAI 兼容的 /v1/chat/completions（真实大模型）；
 *  - 兜底场景下由 {@code RuleBrain} 代替，不实现本接口（它不走网络）。
 */
public interface LlmClient {
    LlmResponse chat(List<LlmMessage> messages, List<ToolSpec> tools);
}
