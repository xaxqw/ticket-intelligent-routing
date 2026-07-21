package com.ticket.agent.tool.impl;

import com.ticket.agent.tool.Args;
import com.ticket.agent.tool.Tool;
import com.ticket.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读工具：内置工单知识库（FAQ）。演示 Agent “检索知识库”这一工具能力。
 * 真实项目中可替换为向量库 / 企业 Wiki / RAG 检索；此处用关键词匹配的本地知识，保证离线可跑。
 */
@Component
public class QueryKnowledgeTool implements Tool {

    private static final Map<String, String> KB = new LinkedHashMap<>();

    static {
        KB.put("报销", "财务类工单：涉及报销、付款、发票、打款、预算、费用等，分派到 finance-group（财务组），通常需初审→复审→财务打款。");
        KB.put("权限", "权限类工单：涉及账号、权限、授权、账号开通、登录、密码等，分派到 permission-group（权限组）。");
        KB.put("硬件", "硬件类工单：涉及设备、网络、电脑、打印机、报修等，分派到 hardware-group（硬件组）。");
        KB.put("打款", "涉及真实资金打款的节点（财务打款 pay）需要严格审批，建议保持人工复核，不要自动放行。");
        KB.put("SLA", "工单有 SLA 时限（默认 24 小时），超时会触发预警；高优先级工单应缩短 SLA。");
        KB.put("复核", "当 AI 分派置信度低于阈值（默认 0.55）时，工单进入“待人工复核”，不自动分派，由人工确认或改判后再启动流程。");
        KB.put("流程", "工单流程：提交→初审→(复审/会签)→补充材料/财务打款→归档。可动态驳回到任意历史节点。");
    }

    @Override
    public String name() {
        return "query_knowledge";
    }

    @Override
    public String description() {
        return "检索工单处理知识库，回答分类标准、审批流程、SLA、人工复核规则等常见问题。";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"keyword\":{\"type\":\"string\",\"description\":\"用户咨询的关键词或问题\"}},"
                + "\"required\":[\"keyword\"]}";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, boolean confirmed) {
        String kw = Args.str(args, "keyword", "").toLowerCase();
        StringBuilder hit = new StringBuilder();
        for (Map.Entry<String, String> e : KB.entrySet()) {
            if (kw.contains(e.getKey()) || e.getKey().contains(kw)) {
                hit.append("· ").append(e.getValue()).append("\n");
            }
        }
        if (hit.length() == 0) {
            hit.append("知识库暂无“").append(kw).append("”相关的明确条目，建议结合分类规则判断。");
        }
        return ToolResult.builder().content("知识库检索结果：\n" + hit).build();
    }
}
