package com.ticket.agent;

import com.ticket.agent.brain.AgentBrain;
import com.ticket.agent.brain.BrainDecision;
import com.ticket.agent.brain.LlmBrain;
import com.ticket.agent.brain.RuleBrain;
import com.ticket.agent.llm.LlmMessage;
import com.ticket.agent.llm.ToolCall;
import com.ticket.agent.llm.ToolSpec;
import com.ticket.agent.tool.PendingAction;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolRegistry;
import com.ticket.agent.tool.ToolResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * Agent 推理引擎（ReAct 循环）。
 *
 * <p>每一轮：把对话历史 + 工具规格交给“大脑”决策 → 若调用工具：
 *  - 读工具：直接执行，结果回灌对话，继续推理；
 *  - 写工具：只生成“待确认动作”（不执行），挂起并结束本轮，等人工确认。
 * 直到大脑给出最终答复或达到最大步数。</p>
 *
 * <p>“写操作不自动执行、需人工确认”即本系统 Agent 层的“人在回路”落点。</p>
 */
@Service
public class AgentEngine {

    @Resource
    private AgentBrain brain;
    @Resource
    private ToolRegistry toolRegistry;
    @Resource
    private AgentSessionStore sessionStore;
    @Resource
    private AgentProperties agentProperties;

    public AgentReply handle(String sessionId, String userMessage) {
        AgentSessionStore.AgentSession session = sessionStore.getOrCreate(sessionId);
        ensureSystem(session);

        session.getMessages().add(LlmMessage.builder()
                .role(LlmMessage.Role.user)
                .content(userMessage)
                .build());

        List<PendingAction> pendingThisTurn = new ArrayList<>();
        String finalText = "";
        int steps = 0;
        boolean awaiting = false;

        for (int i = 0; i < agentProperties.getMaxSteps(); i++) {
            steps++;
            BrainDecision decision = brain.decide(session.getMessages(), toolRegistry.specs());

            if (decision.isFinish()) {
                finalText = decision.getText() == null ? "" : decision.getText();
                session.getMessages().add(LlmMessage.builder()
                        .role(LlmMessage.Role.assistant)
                        .content(finalText)
                        .build());
                break;
            }

            ToolCall call = decision.getToolCall();
            Tool tool = toolRegistry.get(call.getName());
            Map<String, Object> arguments = call.getArguments() == null
                    ? Collections.emptyMap() : call.getArguments();

            if (tool == null) {
                session.getMessages().add(toolResultMsg(call, "未知工具：" + call.getName()));
                continue;
            }

            if (tool.isWrite()) {
                // 不执行，仅生成待确认动作，挂起并结束本轮（等人工确认）
                ToolResult r = tool.execute(arguments, false);
                PendingAction pa = r.getPendingAction();
                if (pa == null) pa = new PendingAction(null, tool.name(), "写操作", arguments);
                sessionStore.registerPending(session, pa);
                pendingThisTurn.add(pa);
                awaiting = true;
                break;
            }

            // 读工具：执行并回灌结果，继续推理
            ToolResult r = tool.execute(arguments, true);
            session.getMessages().add(toolResultMsg(call, r == null ? "" : r.getContent()));
        }

        trimHistory(session);

        AgentReply reply = new AgentReply();
        reply.setSessionId(sessionId);
        reply.setPendingActions(pendingThisTurn);
        reply.setAwaitingConfirmation(awaiting);
        reply.setSteps(steps);
        reply.setMode(brain instanceof LlmBrain ? "llm" : "rule");
        if (awaiting) {
            reply.setReplyText(finalText.isEmpty() ? "我已为你拟定以下操作，请在下方确认后执行：" : finalText);
        } else {
            reply.setReplyText(finalText.isEmpty() ? "（本轮未产生明确结论，可换个说法再试。）" : finalText);
        }
        return reply;
    }

    /** 人工确认 / 取消一个待执行动作。返回执行结果文本。 */
    public String resolvePending(String sessionId, String actionId, boolean approved) {
        AgentSessionStore.AgentSession session = sessionStore.getOrCreate(sessionId);
        PendingAction action = sessionStore.takePending(session, actionId);
        if (action == null) {
            return "未找到该待确认操作（可能已处理或会话已过期）。";
        }
        if (!approved) {
            return "已取消操作：" + action.getSummary();
        }
        Tool tool = toolRegistry.get(action.getToolName());
        if (tool == null) {
            return "工具不存在：" + action.getToolName();
        }
        try {
            ToolResult r = tool.execute(action.getParams() == null ? Collections.emptyMap() : action.getParams(), true);
            // 把执行结果回灌会话，使后续对话有上下文
            session.getMessages().add(LlmMessage.builder()
                    .role(LlmMessage.Role.tool)
                    .name(action.getToolName())
                    .toolCallId(actionId)
                    .content(r == null ? "已执行" : r.getContent())
                    .build());
            return r == null ? "已执行：" + action.getSummary() : r.getContent();
        } catch (Exception e) {
            return "执行失败：" + action.getSummary() + " —— " + e.getMessage();
        }
    }

    private void ensureSystem(AgentSessionStore.AgentSession session) {
        if (session.getMessages().isEmpty()
                || session.getMessages().get(0).getRole() != LlmMessage.Role.system) {
            session.getMessages().add(0, LlmMessage.builder()
                    .role(LlmMessage.Role.system)
                    .content(agentProperties.getSystemPrompt())
                    .build());
        }
    }

    private void trimHistory(AgentSessionStore.AgentSession session) {
        List<LlmMessage> msgs = session.getMessages();
        // 保留 system 在 0 位，最多保留 MAX_HISTORY 条
        while (msgs.size() > AgentSessionStore.MAX_HISTORY && msgs.size() > 1) {
            // 不删 system（索引0）
            msgs.remove(1);
        }
    }

    private LlmMessage toolResultMsg(ToolCall call, String content) {
        return LlmMessage.builder()
                .role(LlmMessage.Role.tool)
                .toolCallId(call.getId())
                .name(call.getName())
                .content(content)
                .build();
    }
}
