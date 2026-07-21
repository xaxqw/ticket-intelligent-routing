package com.ticket.workflow.service;

import com.ticket.common.constants.TicketConstants;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * Flowable 工作流服务。封装流程的启动、任务认领/完成、会签数据准备，
 * 以及“动态驳回”这一企业级关键能力（任意节点跳转）。
 */
@Service
public class WorkflowService {

    @Resource
    private RuntimeService runtimeService;
    @Resource
    private TaskService taskService;
    @Resource
    private RepositoryService repositoryService;
    @Resource
    private HistoryService historyService;

    /**
     * 启动工单流程。
     *
     * @param ticketId      业务主键（作为 Flowable businessKey，便于双向关联）
     * @param title         工单标题（流程变量，可在待办中展示）
     * @param categoryGroup 候选组（决定初审由哪类人员处理，由 AI 分派结果决定）
     * @param reviewers     复审会签人列表
     * @return 流程实例 id
     */
    public String startProcess(String ticketId, String title, String categoryGroup, List<String> reviewers) {
        Map<String, Object> vars = new HashMap<>(8);
        vars.put(TicketConstants.VAR_TICKET_ID, ticketId);
        vars.put("title", title);
        vars.put("categoryGroup", categoryGroup);
        vars.put(TicketConstants.VAR_REVIEWERS, reviewers == null || reviewers.isEmpty()
                ? Arrays.asList("reviewerA", "reviewerB") : reviewers);
        vars.put("rejected", false);
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                TicketConstants.PROCESS_KEY, ticketId, vars);
        return pi.getId();
    }

    /** 某候选人组的可接任务（待办池） */
    public List<Task> tasksByGroup(String candidateGroup) {
        return taskService.createTaskQuery()
                .taskCandidateGroup(candidateGroup)
                .orderByTaskCreateTime().desc()
                .list();
    }

    /** 某用户已认领的任务 */
    public List<Task> tasksByAssignee(String userId) {
        return taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .list();
    }

    /** 某候选用户（如会签审批人）的任务 */
    public List<Task> tasksByCandidateUser(String userId) {
        return taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .list();
    }

    /** 全部待办任务（不区分组/人，管理员/调试用） */
    public List<Task> allTasks() {
        return taskService.createTaskQuery()
                .orderByTaskCreateTime().desc()
                .list();
    }

    /** 认领任务（防止多人同时接单造成重复处理） */
    public void claim(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    /** 完成任务，variables 携带审批结论（如 approved=true/false） */
    public void complete(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables == null ? Collections.emptyMap() : variables);
    }

    /**
     * 动态驳回：将流程实例的“当前活跃任务对应的执行”跳转到目标节点。
     * 注意：moveExecutionToActivityId 的第一个参数必须是【执行实例 id】而非流程实例 id，
     * 因此这里取当前任务的实际 executionId，确保精准且只作用于本流程实例。
     */
    public void rejectDynamic(String processInstanceId, String targetActivityId) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        if (tasks == null || tasks.isEmpty()) {
            throw new org.flowable.common.engine.api.FlowableException("流程无活跃任务，无法动态驳回");
        }
        ChangeActivityStateBuilder builder = runtimeService.createChangeActivityStateBuilder();
        for (Task t : tasks) {
            builder.moveExecutionToActivityId(t.getExecutionId(), targetActivityId);
        }
        builder.changeState();
    }

    /** 当前流程实例所处的活动节点名（可能多个，如会签并行） */
    public List<String> currentNodes(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        List<String> nodes = new ArrayList<>();
        for (Task t : tasks) nodes.add(t.getName());
        return nodes;
    }

    /** 流程实例下的全部活动任务 */
    public List<Task> tasksByProcessInstance(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
    }

    /** 任务名（节点名），用于审计记录 */
    public String taskName(String taskId) {
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        return t == null ? null : t.getName();
    }

    /** 按 id 取任务（含其所属流程实例 id，用于归属校验） */
    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    /** 在流程实例上设置变量（如进入复审前重置 rejected 标志） */
    public void setVariable(String processInstanceId, String key, Object value) {
        if (processInstanceId == null) return;
        runtimeService.setVariable(processInstanceId, key, value);
    }

    /** 删除流程实例：先删运行时（若未结束），再删历史，彻底清理 Flowable 侧数据 */
    public void deleteProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) return;
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (pi != null) {
            runtimeService.deleteProcessInstance(processInstanceId, "ticket deleted by user");
        }
        try {
            historyService.deleteHistoricProcessInstance(processInstanceId);
        } catch (Exception ignore) {
            // 历史不存在或已删除，忽略
        }
    }

    /** 流程是否已结束（流程实例不存在即视为已结束） */
    public boolean isFinished(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult() == null;
    }

    /** 某节点（按活动 id）的历史耗时（毫秒），用于 SLA 聚合 */
    public Long nodeDurationMs(String processInstanceId, String activityId) {
        List<HistoricActivityInstance> list = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .finished()
                .list();
        if (list.isEmpty()) return null;
        long sum = 0;
        for (HistoricActivityInstance hi : list) {
            if (hi.getDurationInMillis() != null) sum += hi.getDurationInMillis();
        }
        return sum;
    }
}
