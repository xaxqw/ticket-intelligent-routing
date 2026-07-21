package com.ticket.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.agent.brain.AgentBrain;
import com.ticket.agent.brain.LlmBrain;
import com.ticket.agent.brain.RuleBrain;
import com.ticket.agent.llm.OpenAiCompatibleLlmClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 层自动装配。
 * 核心：根据 {@code ai.agent.llm.enabled} 选择“大脑”——
 *  开启且配置了 apiKey → 接真实大模型（LlmBrain + OpenAI 兼容客户端）；
 *  否则 → 本地确定性兜底规划器（RuleBrain），无需任何 Key 即可跑通完整 ReAct 流程。
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Bean
    public AgentBrain agentBrain(AgentProperties props, ObjectMapper mapper) {
        AgentProperties.Llm llm = props.getLlm();
        if (llm.isEnabled() && llm.getApiKey() != null && !llm.getApiKey().isEmpty()) {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                    mapper, llm.getBaseUrl(), llm.getApiKey(),
                    llm.getModel(), llm.getTemperature(), llm.getTimeoutMs());
            return new LlmBrain(client);
        }
        return new RuleBrain();
    }
}
