package com.ticket.web.service;

import com.ticket.ai.TicketClassifier;
import com.ticket.common.constants.TicketConstants;
import com.ticket.common.domain.*;
import com.ticket.common.dto.CreateTicketRequest;
import com.ticket.common.dto.RouteResult;
import com.ticket.common.dto.TicketVO;
import com.ticket.common.dto.TaskView;
import com.ticket.common.exception.BizException;
import com.ticket.common.spi.TicketCommandApi;
import com.ticket.common.exception.IdempotencyException;
import com.ticket.common.util.SnowflakeId;
import com.ticket.web.dto.TaskVO;
import com.ticket.web.lock.RedisDistributedLock;
import com.ticket.web.repository.TicketHistoryRepository;
import com.ticket.web.repository.TicketRepository;
import com.ticket.web.notification.NotificationService;
import com.ticket.workflow.service.WorkflowService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单业务编排（核心）：
 *  创建：幂等键 + Redis 锁防重复建单 → AI 语义分派 → 状态机 PENDING→PROCESSING → 启动 Flowable 流程
 *  处理：接单（Redis 锁防并发重复处理）→ 审批（驱动流程变量）→ 挂起/恢复（状态机约束）
 *  驳回：动态跳转任意历史节点（changeActivityState）
 *
 * 所有状态变更都经过 {@link TicketStatus#canTransitionTo} 守卫，杜绝状态混乱。
 */
@Service
public class TicketService implements TicketCommandApi {

    @Resource
    private TicketRepository ticketRepository;
    @Resource
    private TicketHistoryRepository historyRepository;
    @Resource
    private TicketClassifier classifier;
    @Resource
    private WorkflowService workflow;
    @Resource
    private RedisDistributedLock redisLock;
    @Resource
    private SnowflakeId snowflakeId;
    @Resource
    private NotificationService notificationService;

    // ====================== 创建 ======================

    @Transactional
    public TicketVO createTicket(CreateTicketRequest req) {
        String idem = (req.getIdempotencyKey() == null || req.getIdempotencyKey().isEmpty())
                ? UUID.randomUUID().toString() : req.getIdempotencyKey();

        String lockKey = TicketConstants.LOCK_IDEMPOTENT + idem;
        if (!redisLock.tryLock(lockKey, 3, 10)) {
            throw new BizException("SYSTEM_BUSY", "系统繁忙，请稍后重试");
        }
        try {
            // 幂等：重复提交直接返回原工单
            Optional<Ticket> existing = ticketRepository.findByIdempotencyKey(idem);
            if (existing.isPresent()) {
                throw new IdempotencyException(existing.get().getId());
            }

            // AI 语义分派（或人工覆盖）
            Category category;
            Double confidence;
            RouteResult aiResult = null;
            boolean needsReview = false;
            if (req.getCategory() != null) {
                category = Category.fromLabel(req.getCategory());
                confidence = null;
                if (category == null) throw new BizException("非法分类: " + req.getCategory());
            } else {
                RouteResult r = classifier.classify(req.getTitle(), req.getDescription());
                aiResult = r;
                category = r.getCategory() != null ? r.getCategory() : Category.HARDWARE;
                confidence = r.getConfidence();
                needsReview = r.isReviewRequired();
            }

            String id = snowflakeId.nextStr();
            Ticket t = new Ticket(id, idem, req.getTitle(), req.getDescription());
            t.setCategory(category);
            t.setAiConfidence(confidence);
            t.setPriority(req.getPriority());
            t.setSlaDueAt(LocalDateTime.now().plusHours(req.getSlaHours()));
            t.setStatus(TicketStatus.PENDING);
            t = ticketRepository.save(t);

            historyRepository.save(new TicketHistory(snowflakeId.nextStr(), id,
                    TicketConstants.NODE_SUBMIT, "system", TicketConstants.ACTION_SUBMIT));

            // 人在回路：低置信度不自动分派，挂起待人工复核（避免误分流）
            if (needsReview) {
                t.setAiReviewRequired(true);
                t.setUpdatedAt(LocalDateTime.now());
                t = ticketRepository.save(t);
                historyRepository.save(new TicketHistory(snowflakeId.nextStr(), id,
                        TicketConstants.NODE_SUBMIT, "system",
                        "AI 置信度过低(" + confidence + ")，转人工复核:" + category.getLabel()));
                notificationService.notifyReviewNeeded(id, t.getTitle());
                return toVO(t);
            }

            // 启动流程，状态推进到处理中
            String piId = workflow.startProcess(id, req.getTitle(),
                    category.getCandidateGroup(), Collections.emptyList());
            t.setProcessInstanceId(piId);
            t.setStatus(TicketStatus.PROCESSING);
            t.setUpdatedAt(LocalDateTime.now());
            t = ticketRepository.save(t);

            historyRepository.save(new TicketHistory(snowflakeId.nextStr(), id,
                    TicketConstants.NODE_SUBMIT, "system", "分派:" + category.getLabel()));
            notificationService.notifyRouted(id, t.getTitle(), category.getCandidateGroup());
            return toVO(t);
        } finally {
            redisLock.unlock(lockKey);
        }
    }

    // ====================== 查询 ======================

    public TicketVO getTicket(String id) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new BizException("工单不存在: " + id));
        return toVO(t);
    }

    public List<TicketVO> listTickets(String status, String category, Boolean review) {
        List<Ticket> list;
        if (Boolean.TRUE.equals(review)) {
            list = ticketRepository.findByAiReviewRequiredTrue();
        } else if (status != null && !status.isEmpty()) {
            list = ticketRepository.findByStatus(TicketStatus.valueOf(status));
        } else if (category != null && !category.isEmpty()) {
            list = ticketRepository.findByCategory(Category.fromLabel(category));
        } else {
            list = ticketRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<TaskVO> listTasks(String group, String assignee) {
        List<TaskVO> vos = new ArrayList<>();
        for (Task tk : queryTasks(group, assignee)) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(tk.getId());
            vo.setNodeName(tk.getName());
            vo.setAssignee(tk.getAssignee());
            vo.setCreateTime(toLocalDateTime(tk.getCreateTime()));
            fillTaskVo(tk, vo);
            vos.add(vo);
        }
        return vos;
    }

    /**
     * Agent 编排层使用的待办视图（返回 common 层的 {@link TaskView}，避免 agent 依赖 web 模块）。
     * 与 {@link #listTasks} 共用同一查询与映射逻辑。
     */
    @Override
    public List<TaskView> listTaskViews(String group, String assignee) {
        List<TaskView> vos = new ArrayList<>();
        for (Task tk : queryTasks(group, assignee)) {
            TaskView vo = new TaskView();
            vo.setTaskId(tk.getId());
            vo.setNodeName(tk.getName());
            vo.setAssignee(tk.getAssignee());
            vo.setCreateTime(toLocalDateTime(tk.getCreateTime()));
            fillTicketInfo(tk, (id, title) -> { vo.setTicketId(id); vo.setTitle(title); });
            vos.add(vo);
        }
        return vos;
    }

    private List<Task> queryTasks(String group, String assignee) {
        if (assignee != null && !assignee.isEmpty()) {
            // 已认领 + 候选（覆盖会签场景：审批人是 candidateUser 而非 assignee）
            List<Task> tasks = new ArrayList<>();
            tasks.addAll(workflow.tasksByAssignee(assignee));
            tasks.addAll(workflow.tasksByCandidateUser(assignee));
            return tasks;
        } else if (group != null && !group.isEmpty()) {
            return workflow.tasksByGroup(group);
        }
        // 无参数：返回全部待办（跨所有组/人）
        return workflow.allTasks();
    }

    private void fillTicketInfo(Task tk, java.util.function.BiConsumer<String, String> setter) {
        String piId = tk.getProcessInstanceId();
        if (piId != null) {
            ticketRepository.findByProcessInstanceId(piId).ifPresent(t -> setter.accept(t.getId(), t.getTitle()));
        }
    }

    /** 待办视图专用：在 fillTicketInfo 基础上额外透出最近驳回理由 */
    private void fillTaskVo(Task tk, TaskVO vo) {
        String piId = tk.getProcessInstanceId();
        if (piId != null) {
            ticketRepository.findByProcessInstanceId(piId).ifPresent(t -> {
                vo.setTicketId(t.getId());
                vo.setTitle(t.getTitle());
                vo.setRejectReason(t.getLastRejectReason());
            });
        }
    }

    private LocalDateTime toLocalDateTime(java.util.Date d) {
        return d == null ? null : d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
    }

    // ====================== 处理 ======================

    @Transactional
    public void claim(String ticketId, String taskId, String userId) {
        String lockKey = TicketConstants.LOCK_TICKET_CLAIM + ticketId;
        if (!redisLock.tryLock(lockKey, 3, 30)) {
            throw new BizException("CLAIM_CONFLICT", "该工单正被他人处理，请稍后重试");
        }
        try {
            Ticket t = requireTicket(ticketId);
            // 归属校验：任务必须属于本工单的流程实例
            Task task = workflow.getTask(taskId);
            if (task == null || !t.getProcessInstanceId().equals(task.getProcessInstanceId())) {
                throw new BizException("TASK_NOT_BELONG", "该任务不属于此工单");
            }
            t.setAssignee(userId);
            t.setUpdatedAt(LocalDateTime.now());
            ticketRepository.save(t);
            workflow.claim(taskId, userId);
            historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                    TicketConstants.NODE_AUDIT1, userId, TicketConstants.ACTION_CLAIM));
        } finally {
            redisLock.unlock(lockKey);
        }
    }

    @Transactional
    public void completeTask(String ticketId, String taskId, String userId, boolean approved, String reason) {
        Ticket t = requireTicket(ticketId);
        // 归属校验：任务必须属于本工单的流程实例，杜绝跨工单误完成
        Task task = workflow.getTask(taskId);
        if (task == null || !t.getProcessInstanceId().equals(task.getProcessInstanceId())) {
            throw new BizException("TASK_NOT_BELONG", "该任务不属于此工单");
        }
        String node = workflow.taskName(taskId);
        boolean ticketChanged = false;
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("approved", approved);
        // 初审通过即进入复审：重置驳回标志与上一轮驳回理由，避免污染本轮（否则会签会死循环）
        if ("初审".equals(node) && approved) {
            workflow.setVariable(t.getProcessInstanceId(), "rejected", false);
            if (t.getLastRejectReason() != null) { t.setLastRejectReason(null); ticketChanged = true; }
        }
        // 驳回：记录理由到流程变量 + 工单 + 历史
        String r = (reason == null) ? "" : reason.trim();
        if (!approved) {
            vars.put(TicketConstants.VAR_REJECT_REASON, r);
            t.setLastRejectReason(r);
            ticketChanged = true;
        }
        workflow.complete(taskId, vars);
        historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                node, userId, approved ? TicketConstants.ACTION_APPROVE : TicketConstants.ACTION_REJECT,
                approved ? null : r));
        if (approved) {
            notificationService.notifyApproved(ticketId, t.getTitle(), node);
        } else {
            notificationService.notifyRejected(ticketId, t.getTitle(), r, node);
        }
        if (ticketChanged) {
            t.setUpdatedAt(LocalDateTime.now());
            ticketRepository.save(t);
        }
    }

    @Transactional
    public void suspend(String ticketId) {
        Ticket t = requireTicket(ticketId);
        t.getStatus().assertTransition(TicketStatus.SUSPENDED);
        t.setStatus(TicketStatus.SUSPENDED);
        t.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(t);
        historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                null, "system", TicketConstants.ACTION_SUSPEND));
    }

    @Transactional
    public void resume(String ticketId) {
        Ticket t = requireTicket(ticketId);
        t.getStatus().assertTransition(TicketStatus.PROCESSING);
        t.setStatus(TicketStatus.PROCESSING);
        t.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(t);
        historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                null, "system", TicketConstants.ACTION_RESUME));
    }

    /** 节点名 -> BPMN activityId 映射，用于动态驳回时定位当前节点 */
    private static final java.util.Map<String, String> NODE_TO_ACTIVITY = new java.util.HashMap<>();
    static {
        NODE_TO_ACTIVITY.put("初审", "audit1");
        NODE_TO_ACTIVITY.put("复审", "audit2");
        NODE_TO_ACTIVITY.put("补充材料", "revise");
        NODE_TO_ACTIVITY.put("财务打款", "pay");
        NODE_TO_ACTIVITY.put("归档", "archive");
    }

    /** 动态驳回：跳转到指定历史节点（activityId: audit1 / revise / pay）。
     *  若工单已归档(COMPLETED)，则视为“重开”：启动新流程实例并落到目标节点，状态回退为处理中。 */
    @Transactional
    public void rejectDynamic(String ticketId, String targetActivityId, String reason) {
        Ticket t = requireTicket(ticketId);
        String r = (reason == null) ? "" : reason.trim();
        if (t.getStatus() == TicketStatus.COMPLETED) {
            // 已归档工单重开：启动新流程实例（落在初审），再按需跳到目标节点
            String piId = workflow.startProcess(t.getId(), t.getTitle(),
                    t.getCategory().getCandidateGroup(), Collections.emptyList());
            t.setProcessInstanceId(piId);
            t.setStatus(TicketStatus.PROCESSING);
            t.setLastRejectReason(r);
            t.setUpdatedAt(LocalDateTime.now());
            ticketRepository.save(t);
            if (!"audit1".equals(targetActivityId)) {
                try {
                    workflow.rejectDynamic(piId, targetActivityId);
                    workflow.setVariable(piId, TicketConstants.VAR_REJECT_REASON, r);
                } catch (Exception e) {
                    throw new BizException("REJECT_FAILED", "重开并驳回失败：" + e.getMessage());
                }
            }
            historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                    targetActivityId, "system", "重开并驳回->" + targetActivityId, r));
            notificationService.notifyRejected(ticketId, t.getTitle(), r, "重开→" + targetActivityId);
            return;
        }
        if (t.getProcessInstanceId() == null) throw new BizException("工单尚未进入流程");
        // PROCESSING：仅支持单节点（并行会签节点不支持动态驳回）
        List<String> nodes = workflow.currentNodes(t.getProcessInstanceId());
        if (nodes.size() != 1) {
            throw new BizException("REJECT_UNSUPPORTED", "当前为并行/多节点（如会签），不支持动态驳回");
        }
        if (!NODE_TO_ACTIVITY.containsKey(nodes.get(0))) {
            throw new BizException("REJECT_UNSUPPORTED", "不支持的当前节点：" + nodes.get(0));
        }
        try {
            workflow.rejectDynamic(t.getProcessInstanceId(), targetActivityId);
            workflow.setVariable(t.getProcessInstanceId(), TicketConstants.VAR_REJECT_REASON, r);
        } catch (Exception e) {
            throw new BizException("REJECT_FAILED", "动态驳回失败：" + e.getMessage());
        }
        t.setLastRejectReason(r);
        t.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(t);
        historyRepository.save(new TicketHistory(snowflakeId.nextStr(), ticketId,
                targetActivityId, "system", "动态驳回->" + targetActivityId, r));
        notificationService.notifyRejected(ticketId, t.getTitle(), r, "驳回至" + targetActivityId);
    }

    /** 删除工单：连带清理 Flowable 流程实例（运行时+历史）与工单历史记录 */
    @Transactional
    public void deleteTicket(String id) {
        Ticket t = requireTicket(id);
        if (t.getProcessInstanceId() != null) {
            workflow.deleteProcessInstance(t.getProcessInstanceId());
        }
        List<TicketHistory> hs = historyRepository.findByTicketIdOrderByCreatedAtAsc(id);
        if (!hs.isEmpty()) {
            historyRepository.deleteAll(hs);
        }
        ticketRepository.delete(t);
    }

    // ====================== AI 演示 ======================

    public RouteResult routeOnly(String title, String description) {
        return classifier.classify(title, description);
    }

    // ====================== 主动学习（人在回路闭环的延伸） ======================

    /**
     * 不确定样本池（主动学习采集环节）：返回当前“低置信、待人工复核”的工单，
     * 即模型最不确定、最值得人工标注的样本。人工标注后通过 labelFeedback 回流，
     * 使向量库越用越准——这是主动学习(active learning)的核心采集环节。
     */
    public List<Map<String, Object>> uncertaintyPool() {
        List<Ticket> list = ticketRepository.findByAiReviewRequiredTrue();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ticket t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("predictedCategory", t.getCategory() != null ? t.getCategory().getLabel() : null);
            m.put("confidence", t.getAiConfidence());
            out.add(m);
        }
        return out;
    }

    /**
     * 标注回流（主动学习的标注注入环节）：把人工最终结论批量写回向量库，
     * 下次同类工单更容易被正确分派。items 元素形如 {"text":"...","category":"硬件类"}。
     */
    public int labelFeedback(List<Map<String, String>> items) {
        int n = 0;
        for (Map<String, String> it : items) {
            String text = it.get("text");
            String cat = it.get("category");
            if (text == null || text.trim().isEmpty() || cat == null) continue;
            Category c = Category.fromLabel(cat);
            if (c == null) continue;
            classifier.learn(text, c);
            n++;
        }
        return n;
    }

    /**
     * 人工复核 AI 分派：低置信工单经人工确认（或改判）后正式启动审批流程。
     * 这是“人在回路”的闭环——AI 不盲目自动分派，不确定时交人定夺，
     * 且此处可把人工最终结论回流进向量库（在线学习），使系统越用越准。
     */
    public TicketVO confirmAiReview(String id, String categoryLabel) {
        Ticket t = requireTicket(id);
        if (!Boolean.TRUE.equals(t.getAiReviewRequired())) {
            throw new BizException("该工单无需 AI 复核");
        }
        Category finalCat = categoryLabel != null ? Category.fromLabel(categoryLabel) : t.getCategory();
        if (finalCat == null) throw new BizException("非法分类: " + categoryLabel);

        // 人工最终结论回流向量库（在线学习：让同类工单下次更容易被正确分派）
        classifier.learn(t.getTitle() + " " + t.getDescription(), finalCat);

        t.setCategory(finalCat);
        t.setAiReviewRequired(false);
        t.setUpdatedAt(LocalDateTime.now());

        String piId = workflow.startProcess(id, t.getTitle(), finalCat.getCandidateGroup(), Collections.emptyList());
        t.setProcessInstanceId(piId);
        t.setStatus(TicketStatus.PROCESSING);
        t = ticketRepository.save(t);
        notificationService.notifyRouted(id, t.getTitle(), finalCat.getCandidateGroup());

        historyRepository.save(new TicketHistory(snowflakeId.nextStr(), id,
                TicketConstants.NODE_SUBMIT, "reviewer", "AI复核确认分派:" + finalCat.getLabel()));
        return toVO(t);
    }

    // ====================== 工具 ======================

    private Ticket requireTicket(String id) {
        return ticketRepository.findById(id).orElseThrow(() -> new BizException("工单不存在: " + id));
    }

    private TicketVO toVO(Ticket t) {
        TicketVO vo = TicketVO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .category(t.getCategory())
                .aiConfidence(t.getAiConfidence())
                .aiReviewRequired(t.getAiReviewRequired())
                .status(t.getStatus())
                .priority(t.getPriority())
                .assignee(t.getAssignee())
                .processInstanceId(t.getProcessInstanceId())
                .lastRejectReason(t.getLastRejectReason())
                .createdAt(t.getCreatedAt())
                .slaDueAt(t.getSlaDueAt())
                .completedAt(t.getCompletedAt())
                .build();
        if (t.getStatus() != TicketStatus.COMPLETED && t.getProcessInstanceId() != null) {
            vo.setCurrentNode(String.join(",", workflow.currentNodes(t.getProcessInstanceId())));
        }
        return vo;
    }
}
