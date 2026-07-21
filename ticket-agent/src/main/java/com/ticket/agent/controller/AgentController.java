package com.ticket.agent.controller;

import com.ticket.agent.AgentEngine;
import com.ticket.agent.AgentProperties;
import com.ticket.agent.AgentReply;
import com.ticket.agent.llm.ToolSpec;
import com.ticket.agent.tool.ToolRegistry;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * Agent 编排层 REST 接口。
 *  - POST /api/agent/chat   发送一句自然语言，返回 Agent 的答复 + 待确认动作
 *  - POST /api/agent/confirm 人工确认/取消某个待执行写操作
 *  - GET  /api/agent/tools  当前可用工具清单（透明化）
 *  - GET  /api/agent/mode   当前智能来源（llm / rule）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Resource
    private AgentEngine agentEngine;
    @Resource
    private ToolRegistry toolRegistry;
    @Resource
    private AgentProperties agentProperties;

    @PostMapping("/chat")
    public AgentReply chat(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "");
        String message = body.getOrDefault("message", "");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (message == null || message.trim().isEmpty()) {
            AgentReply r = new AgentReply();
            r.setSessionId(sessionId);
            r.setReplyText("请输入你想让我帮忙做的事，例如“新建工单：笔记本无法开机”。");
            r.setMode(agentProperties.getLlm().isEnabled() ? "llm" : "rule");
            return r;
        }
        return agentEngine.handle(sessionId, message);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String actionId = (String) body.get("actionId");
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String result = agentEngine.resolvePending(sessionId, actionId, approved);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("approved", approved);
        out.put("result", result);
        return out;
    }

    @GetMapping("/tools")
    public List<ToolSpec> tools() {
        return toolRegistry.specs();
    }

    @GetMapping("/mode")
    public Map<String, Object> mode() {
        boolean llm = agentProperties.getLlm().isEnabled();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("llmEnabled", llm);
        m.put("mode", llm ? "llm" : "rule");
        return m;
    }
}
