package com.ticket.agent;

import com.ticket.agent.llm.LlmMessage;
import com.ticket.agent.tool.PendingAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话存储（内存）。保存每轮的对话历史与“待人工确认”的动作。
 * 演示场景用内存即可；生产可替换为 Redis（会话跨实例共享）。
 */
@Component
public class AgentSessionStore {

    static final int MAX_HISTORY = 40;

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public AgentSession getOrCreate(String id) {
        return sessions.computeIfAbsent(id, AgentSession::new);
    }

    public void registerPending(AgentSession session, PendingAction action) {
        if (action.getId() == null) action.setId("act-" + UUID.randomUUID().toString().substring(0, 8));
        session.pending.put(action.getId(), action);
    }

    public PendingAction takePending(AgentSession session, String actionId) {
        return session.pending.remove(actionId);
    }

    public boolean hasPending(AgentSession session, String actionId) {
        return session.pending.containsKey(actionId);
    }

    /** 单会话状态 */
    public static class AgentSession {
        private final String id;
        private final List<LlmMessage> messages = new ArrayList<>();
        private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();

        public AgentSession(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public List<LlmMessage> getMessages() {
            return messages;
        }

        public Map<String, PendingAction> getPending() {
            return pending;
        }
    }
}
