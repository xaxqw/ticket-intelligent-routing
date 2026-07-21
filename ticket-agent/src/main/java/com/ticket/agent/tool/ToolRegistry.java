package com.ticket.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.agent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 工具注册表。收集所有 {@link Tool} Bean，按名称索引，并对外暴露“工具规格”给大脑/大模型。
 * 新增工具只需加一个 {@code @Component} 实现 {@link Tool}，自动被收录——这是 Agent 的能力扩展点。
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper;

    public ToolRegistry(List<Tool> toolList, ObjectMapper mapper) {
        this.mapper = mapper;
        if (toolList != null) {
            for (Tool t : toolList) tools.put(t.name(), t);
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /** 生成给大模型/规划器的工具规格列表 */
    public List<ToolSpec> specs() {
        List<ToolSpec> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            Map<String, Object> params;
            try {
                params = mapper.readValue(t.parametersJsonSchema(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                params = Collections.emptyMap();
            }
            out.add(ToolSpec.builder()
                    .name(t.name())
                    .description(t.description())
                    .parameters(params)
                    .build());
        }
        return out;
    }
}
