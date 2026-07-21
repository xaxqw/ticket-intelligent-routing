package com.ticket.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 编排层配置（前缀 ai.agent）。
 *
 * <p>核心开关：{@code ai.agent.llm.enabled}。
 * 默认 false —— 此时使用本地确定性“兜底规划器”(RuleBrain)，无需任何 API Key 即可完整跑通 ReAct 流程，
 * 适合演示与离线环境；一旦填好 OpenAI 兼容的 baseUrl/apiKey/model，置为 true 即切换为真实大模型驱动。</p>
 */
@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {

    /** 是否启用 Agent 层（总开关） */
    private boolean enabled = true;

    /** ReAct 单轮最大推理步数，防止无限循环 */
    private int maxSteps = 6;

    /** 系统提示词（定义 Agent 角色与“写操作需人工确认”的规则） */
    private String systemPrompt = "你是企业工单系统的智能协作 Agent。你可以调用工具来查询工单、查看待办、理解分类规则、"
            + "检索知识库，以及创建工单、分派工单、升级工单。涉及“创建/分派/升级”等会改动数据的写操作，"
            + "你必须调用对应工具并停止，由用户在界面上确认后才执行（人在回路）。请基于工具返回的事实作答，"
            + "不要编造工单内容。";

    private Llm llm = new Llm();

    @Data
    public static class Llm {
        /** 是否启用真实大模型（OpenAI 兼容 /v1/chat/completions）。false 时走本地兜底规划器 */
        private boolean enabled = false;
        /** OpenAI 兼容接口的 baseUrl（如 DeepSeek / Qwen / 本地 Ollama 的 /v1） */
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private double temperature = 0.2;
        private int timeoutMs = 30000;
    }
}
