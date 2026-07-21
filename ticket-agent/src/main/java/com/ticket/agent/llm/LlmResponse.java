package com.ticket.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 大模型一次推理的返回：最终文本（content）和/或 工具调用（toolCalls）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {
    private String content;
    private List<ToolCall> toolCalls;
}
