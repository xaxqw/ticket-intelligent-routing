package com.ticket.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 待人工确认的写操作。Agent 在推理中“提议”该动作但不执行，
 * 前端展示给用户，用户确认后由 {@code AgentController} 用同样的 toolName+params 重新执行（confirmed=true）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingAction {
    private String id;
    private String toolName;
    /** 给人看的一句摘要，如“建单：财务类『报销差旅费』” */
    private String summary;
    /** 执行该动作所需的入参（不含 confirmed 标志） */
    private Map<String, Object> params;
}
