package com.ticket.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 暴露给大模型（或兜底规划器）的工具规格：名称 + 描述 + 入参 JSON Schema。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSpec {
    private String name;
    private String description;
    /** 入参 JSON Schema（对象），如 {"type":"object","properties":{...},"required":[...]} */
    private Map<String, Object> parameters;
}
