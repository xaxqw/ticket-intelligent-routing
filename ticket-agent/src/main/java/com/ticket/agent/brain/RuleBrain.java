package com.ticket.agent.brain;

import com.ticket.agent.llm.LlmMessage;
import com.ticket.agent.llm.ToolCall;
import com.ticket.agent.llm.ToolSpec;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地确定性“兜底规划器”：无需任何 API Key 即可驱动完整的 ReAct 工具循环。
 *
 * <p>它用规则从用户自然语言里识别意图（建单 / 查工单 / 列表 / 派单 / 升级 / 分类 / 知识库 / 路由规则），
 * 并像 LLM 一样“决定调用哪个工具”。对需要 taskId 的“派单”，它会先调 list_my_tasks 拿到待办，
 * 再在下一轮从结果里取出任务去认领——从而真正体现“多步工具调用”。</p>
 *
 * <p>这就保证了：无论是否接入大模型，Agent 的架构（工具注册表、推理循环、人工确认）都真实可运行、可演示。</p>
 */
public class RuleBrain implements AgentBrain {

    private static final Pattern TASK_LINE = Pattern.compile(
            "taskId=([^;]+);\\s*ticketId=([^;]+)");
    private static final Pattern TICKET_ID = Pattern.compile(
            "(?:工单|编号|单号)\\s*[:：]?\\s*(\\d{8,})");

    @Override
    public BrainDecision decide(List<LlmMessage> history, List<ToolSpec> tools) {
        try {
            LlmMessage lastUser = lastOf(history, LlmMessage.Role.user);
            LlmMessage lastTool = lastOf(history, LlmMessage.Role.tool);
            if (lastUser == null) {
                return BrainDecision.finish("你好，我是工单智能协作 Agent。你可以让我查工单、看待办、"
                        + "创建工单、分派或升级工单，也可以问我分类规则与知识库。");
            }
            String msg = lastUser.getContent() == null ? "" : lastUser.getContent().trim();

            // 多步衔接：上一轮刚查过待办，若用户意图是“派单/升级”且未给具体单号，则从结果里取第一条继续
            if (lastTool != null && "list_my_tasks".equals(lastTool.getName())) {
                String firstTaskTicket = null, firstTaskId = null;
                Matcher m = TASK_LINE.matcher(lastTool.getContent() == null ? "" : lastTool.getContent());
                if (m.find()) {
                    firstTaskId = m.group(1).trim();
                    firstTaskTicket = m.group(2).trim();
                }
                if (hasIntent(history, "assign") && firstTaskTicket != null) {
                    String userId = extractUserId(msg);
                    if (userId != null) {
                        return call("assign_ticket", args(
                                "ticketId", firstTaskTicket, "taskId", firstTaskId, "userId", userId));
                    }
                }
                if (hasIntent(history, "escalate") && firstTaskTicket != null) {
                    return call("escalate_ticket", args(
                            "ticketId", firstTaskTicket, "targetActivityId", "audit2"));
                }
                // 只是想看待办，给出收尾提示
                return BrainDecision.finish("以上是当前待办任务。若要对某条说“派给张三”，我会先定位任务再分派（需你确认）。");
            }

            // 若已执行过“非 list_my_tasks”的工具，说明本轮动作已完成：把工具返回的真实内容作为答复（透明化）
            if (lastTool != null) {
                String toolOut = lastTool.getContent() == null ? "" : lastTool.getContent();
                return BrainDecision.finish(toolOut.isEmpty()
                        ? "已完成查询/处理，如需进一步操作请继续说明。"
                        : toolOut);
            }

            // 首轮：按最新用户消息识别意图
            // 1) 分类意图
            if (matches(msg, "分类|分派到哪|属于哪类|归到哪|判断.*类别|应分到|属于什么类")) {
                return call("classify_intent", args("title", msg, "description", msg));
            }
            // 2) 知识库
            if (matches(msg, "知识库|怎么处理|为什么|规则是|能做什么|帮助|如何|是什么意思|FAQ|常见问题")) {
                return call("query_knowledge", args("keyword", msg));
            }
            // 3) 路由/分类规则
            if (matches(msg, "路由规则|分类规则|分派规则|哪些分类|有哪几类|候选组|处理组")) {
                return call("get_routing_rules", args());
            }
            // 4) 升级
            if (matches(msg, "升级|上报|提级|升级到|上报给")) {
                String tid = extractTicketId(msg);
                if (tid != null) {
                    return call("escalate_ticket", args("ticketId", tid, "targetActivityId", "audit2"));
                }
                // 没给单号：先列待办，下一轮自动取第一条升级
                return call("list_my_tasks", args());
            }
            // 5) 派单
            if (matches(msg, "派|分派|接单|指派|分配给|派给|分配给谁|让.*处理|交给")) {
                String userId = extractUserId(msg);
                if (userId == null) {
                    return BrainDecision.finish("好的，要分派给哪位处理人？请告诉我处理人姓名（例如“把这条工单派给张三”），"
                            + "我也可以先列出待办由你指定。");
                }
                // 先查待办，下一轮从中取任务认领
                return call("list_my_tasks", args("assignee", userId));
            }
            // 6) 查具体工单
            String tid = extractTicketId(msg);
            if (tid != null && matches(msg, "查|看|状态|详情|工单")) {
                return call("query_ticket", args("id", tid));
            }
            // 7) 列表 / 待办
            if (matches(msg, "列表|所有工单|全部工单|待复核|待人工|有什么工单|我的工单|列一下")) {
                Map<String, Object> a = new LinkedHashMap<>();
                if (matches(msg, "待复核|待人工|人工复核")) a.put("review", Boolean.TRUE);
                return call("list_tickets", a);
            }
            if (matches(msg, "待办|任务|待我|我的待办|要处理")) {
                return call("list_my_tasks", args());
            }
            // 8) 建单
            if (matches(msg, "新建|建单|提交工单|开单|创建工单|报修|报销|申请权限|申请开通|提个?工单|开个?工单|建个?工单")) {
                String title = stripPrefix(msg);
                return call("create_ticket", args(
                        "title", title.isEmpty() ? msg : title,
                        "description", msg));
            }
            // 9) 兜底
            return BrainDecision.finish("我可以帮你：\n"
                    + "· 创建工单（如“新建工单：笔记本无法开机”）\n"
                    + "· 查询/列表工单（如“查工单 1234567890”“列出待复核工单”）\n"
                    + "· 分派工单（如“把工单派给张三”）\n"
                    + "· 升级工单（如“把工单 1234567890 升级到复审”）\n"
                    + "· 理解分类（如“这条工单属于哪类”）\n"
                    + "· 查知识库 / 路由规则");
        } catch (Exception e) {
            return BrainDecision.finish("（兜底规划器解析异常：" + e.getMessage() + "）");
        }
    }

    // ===================== 工具方法 =====================

    private LlmMessage lastOf(List<LlmMessage> history, LlmMessage.Role role) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == role) return history.get(i);
        }
        return null;
    }

    private boolean hasIntent(List<LlmMessage> history, String kind) {
        for (LlmMessage m : history) {
            if (m.getRole() == LlmMessage.Role.user && m.getContent() != null) {
                String c = m.getContent();
                if ("assign".equals(kind) && matches(c, "派|分派|接单|指派|分配给|派给|让.*处理|交给")) return true;
                if ("escalate".equals(kind) && matches(c, "升级|上报|提级")) return true;
            }
        }
        return false;
    }

    private boolean matches(String text, String regex) {
        if (text == null) return false;
        return Pattern.compile(regex).matcher(text).find();
    }

    private String extractUserId(String msg) {
        Matcher m = Pattern.compile(
                "派给\\s*([^\\s,，。；;]+)|分配给\\s*([^\\s,，。；;]+)|让\\s*([^\\s,，。；;]+)\\s*处理"
                        + "|交给\\s*([^\\s,，。；;]+)|给\\s*([^\\s,，。；;]+)\\s*处理").matcher(msg);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) return m.group(i).trim();
            }
        }
        return null;
    }

    private String extractTicketId(String msg) {
        Matcher m = TICKET_ID.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    private String stripPrefix(String msg) {
        return msg.replaceAll("^(新建工单|新建|建单|提交工单|开单|创建工单|报修|报销|申请权限|申请开通|提个?工单|开个?工单|建个?工单)[：:，,\\s]*", "")
                .trim();
    }

    private BrainDecision call(String name, Map<String, Object> args) {
        return BrainDecision.callTool(ToolCall.builder()
                .id("rule-" + UUID.randomUUID().toString().substring(0, 8))
                .name(name)
                .arguments(args)
                .build());
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
