package com.ticket.agent.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI 兼容的 LLM 客户端（/v1/chat/completions）。
 * 支持 tool calling：把 {@link ToolSpec} 原样透传为 OpenAI function 工具，
 * 把 {@link ToolCall} 中的 arguments（Map）序列化为模型要求的 JSON 字符串。
 *
 * <p>与具体厂商解耦：baseUrl/apiKey/model 均可配置，因此 DeepSeek、通义千问、本地 Ollama
 * 等任何兼容 /v1/chat/completions 的端点都能直接接入——这正是“可插拔大模型”的体现。</p>
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;

    public OpenAiCompatibleLlmClient(ObjectMapper mapper, String baseUrl, String apiKey,
                                     String model, double temperature, int timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.rest = new RestTemplate(factory);
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<ToolSpec> tools) {
        String url = this.baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", toWireMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", toWireTools(tools));
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("LLM 调用失败: HTTP " + resp.getStatusCode() + " body=" + resp.getBody());
        }
        return parse(resp.getBody());
    }

    private List<Map<String, Object>> toWireMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LlmMessage m : messages) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("role", m.getRole().name());
            if (m.getContent() != null) {
                w.put("content", m.getContent());
            }
            if (m.getRole() == LlmMessage.Role.tool) {
                w.put("tool_call_id", m.getToolCallId());
                w.put("name", m.getName());
            }
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall tc : m.getToolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", tc.getName());
                    try {
                        fn.put("arguments", mapper.writeValueAsString(
                                tc.getArguments() == null ? Collections.emptyMap() : tc.getArguments()));
                    } catch (Exception e) {
                        fn.put("arguments", "{}");
                    }
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put("id", tc.getId());
                    call.put("type", "function");
                    call.put("function", fn);
                    calls.add(call);
                }
                w.put("tool_calls", calls);
            }
            out.add(w);
        }
        return out;
    }

    private List<Map<String, Object>> toWireTools(List<ToolSpec> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolSpec t : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.getName());
            fn.put("description", t.getDescription());
            fn.put("parameters", t.getParameters() == null ? Collections.emptyMap() : t.getParameters());
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("type", "function");
            wrap.put("function", fn);
            out.add(wrap);
        }
        return out;
    }

    private LlmResponse parse(String json) {
        try {
            Map<String, Object> root = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<?> choices = (List<?>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmResponse.builder().content("(模型未返回内容)").build();
            }
            Map<String, Object> msg = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("message");

            String content = msg.get("content") == null ? null : msg.get("content").toString();
            List<ToolCall> toolCalls = new ArrayList<>();

            Object rawCalls = msg.get("tool_calls");
            if (rawCalls instanceof List) {
                for (Object rc : (List<?>) rawCalls) {
                    Map<?, ?> call = (Map<?, ?>) rc;
                    Map<?, ?> fn = (Map<?, ?>) call.get("function");
                    String name = fn.get("name").toString();
                    String argsStr = fn.get("arguments") == null ? "{}" : fn.get("arguments").toString();
                    Map<String, Object> args;
                    try {
                        args = mapper.readValue(argsStr, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        args = Collections.emptyMap();
                    }
                    toolCalls.add(ToolCall.builder()
                            .id(call.get("id") == null ? "call-" + UUID.randomUUID() : call.get("id").toString())
                            .name(name)
                            .arguments(args)
                            .build());
                }
            }
            return LlmResponse.builder().content(content).toolCalls(toolCalls).build();
        } catch (Exception e) {
            throw new IllegalStateException("解析 LLM 返回失败: " + e.getMessage(), e);
        }
    }
}
