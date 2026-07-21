package com.ticket.web.dashboard;

import com.ticket.common.constants.TicketConstants;
import com.ticket.web.dto.SlaNodeStat;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * SLA 监控聚合。基于 Flowable 历史活动实例，统计各流程节点平均/最大处理时长，
 * 暴露超过阈值的“慢节点”——这正是仪表盘要回答的核心问题。
 */
@Service
public class SlaService {

    /** 单节点 SLA 阈值（毫秒），超过即标记为慢节点。生产可按环节分别配置。 */
    private static final long NODE_SLA_THRESHOLD_MS = 3_600_000L; // 1 小时

    @Resource
    private HistoryService historyService;

    @Resource
    private RepositoryService repositoryService;

    public List<SlaNodeStat> nodeStats() {
        String procDefId = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(TicketConstants.PROCESS_KEY)
                .latestVersion()
                .singleResult().getId();
        List<HistoricActivityInstance> all = historyService.createHistoricActivityInstanceQuery()
                .processDefinitionId(procDefId)
                .finished()
                .list();

        Map<String, List<Long>> byNode = new LinkedHashMap<>();
        for (HistoricActivityInstance hi : all) {
            if (hi.getDurationInMillis() == null) continue;
            // 只统计实质性处理节点（用户/服务任务），跳过网关、起止事件
            if (!"userTask".equals(hi.getActivityType()) && !"serviceTask".equals(hi.getActivityType())) {
                continue;
            }
            byNode.computeIfAbsent(hi.getActivityName(), k -> new ArrayList<>()).add(hi.getDurationInMillis());
        }

        List<SlaNodeStat> stats = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : byNode.entrySet()) {
            List<Long> ds = e.getValue();
            long sum = 0, max = 0;
            for (long d : ds) { sum += d; if (d > max) max = d; }
            SlaNodeStat s = new SlaNodeStat();
            s.setNodeName(e.getKey());
            s.setAvgDurationMs(sum / ds.size());
            s.setMaxDurationMs(max);
            s.setCount(ds.size());
            s.setSlow(s.getAvgDurationMs() > NODE_SLA_THRESHOLD_MS);
            stats.add(s);
        }
        // 按平均时长降序，慢节点排前面
        stats.sort((a, b) -> Long.compare(b.getAvgDurationMs(), a.getAvgDurationMs()));
        return stats;
    }
}
