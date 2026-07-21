package com.ticket.agent.tool;

import java.util.Map;

/**
 * Agent 可调用的工具。读工具直接执行并返回结果；写工具（isWrite=true）在未确认时
 * 仅产出 {@link PendingAction}（不真正改数据），确认后才执行——这是“人在回路”的落点。
 */
public interface Tool {

    /** 工具名（与大脑产出 / LLM function 名一致） */
    String name();

    /** 工具说明（透传给大模型作为 function 描述） */
    String description();

    /** 入参 JSON Schema（字符串形式，如 {"type":"object","properties":{...},"required":[...]}） */
    String parametersJsonSchema();

    /** 是否为写操作（建单/分派/升级） */
    boolean isWrite();

    /**
     * 执行工具。
     * @param args    入参（由大脑/LLM 解析得到）
     * @param confirmed 写操作是否已获人工确认；读操作忽略此参数
     */
    ToolResult execute(Map<String, Object> args, boolean confirmed);
}
