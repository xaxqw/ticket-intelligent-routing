package com.ticket.agent.brain;

import com.ticket.agent.llm.ToolCall;
import lombok.Getter;

/** 大脑的一次决策：结束作答，或调用一个工具。 */
@Getter
public class BrainDecision {

    public enum Type { FINISH, CALL_TOOL }

    private final Type type;
    private final String text;
    private final ToolCall toolCall;

    private BrainDecision(Type type, String text, ToolCall toolCall) {
        this.type = type;
        this.text = text;
        this.toolCall = toolCall;
    }

    public static BrainDecision finish(String text) {
        return new BrainDecision(Type.FINISH, text, null);
    }

    public static BrainDecision callTool(ToolCall call) {
        return new BrainDecision(Type.CALL_TOOL, null, call);
    }

    public boolean isFinish() {
        return type == Type.FINISH;
    }
}
