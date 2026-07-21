package com.ticket.agent;

import com.ticket.agent.tool.PendingAction;
import lombok.Data;

import java.util.List;

/** Agent 一次对话的返回：最终答复 + 本轮产生的待确认写操作 + 元信息（便于前端透明展示推理过程）。 */
@Data
public class AgentReply {
    private String sessionId;
    /** 智能体给用户的自然语言答复 */
    private String replyText;
    /** 本轮 Agent 提议、等待人工确认的写操作 */
    private List<PendingAction> pendingActions;
    /** 是否存在待确认操作（前端据此渲染确认卡片） */
    private boolean awaitingConfirmation;
    /** 本轮推理步数（透明化：让用户看到 Agent 走了几步工具调用） */
    private int steps;
    /** 当前智能来源：llm（真实大模型）| rule（本地兜底规划器） */
    private String mode;
}
