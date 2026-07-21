package com.ticket.web.controller;

import com.ticket.common.dto.CreateTicketRequest;
import com.ticket.common.dto.TicketVO;
import com.ticket.web.dto.ApiResponse;
import com.ticket.web.dto.TaskVO;
import com.ticket.web.service.TicketService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Resource
    private TicketService ticketService;

    /** 提交工单：幂等建单 + AI 分派 + 启动流程 */
    @PostMapping
    public ApiResponse<TicketVO> create(@Valid @RequestBody CreateTicketRequest req) {
        return ApiResponse.ok(ticketService.createTicket(req));
    }

    /** 工单列表（可按状态/分类过滤；review=true 仅看待 AI 人工复核的工单） */
    @GetMapping
    public ApiResponse<List<TicketVO>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) Boolean review) {
        return ApiResponse.ok(ticketService.listTickets(status, category, review));
    }

    /** 人工复核 AI 分派：确认或改判后启动审批流程（人在回路闭环） */
    @PostMapping("/{id}/ai-confirm")
    public ApiResponse<TicketVO> aiConfirm(@PathVariable String id,
                                           @RequestParam(required = false) String category) {
        return ApiResponse.ok(ticketService.confirmAiReview(id, category));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketVO> get(@PathVariable String id) {
        return ApiResponse.ok(ticketService.getTicket(id));
    }

    /** 删除工单：连带清理流程实例与历史记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        ticketService.deleteTicket(id);
        return ApiResponse.ok();
    }

    /** 接单（Redis 分布式锁防止并发重复处理） */
    @PostMapping("/{id}/claim")
    public ApiResponse<Void> claim(@PathVariable String id,
                                   @RequestParam String taskId,
                                   @RequestParam String userId) {
        ticketService.claim(id, taskId, userId);
        return ApiResponse.ok();
    }

    /** 审批/处理当前任务，approved 决定流程走向；reason 为驳回理由（选填） */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable String id,
                                      @RequestParam String taskId,
                                      @RequestParam String userId,
                                      @RequestParam boolean approved,
                                      @RequestParam(required = false) String reason) {
        ticketService.completeTask(id, taskId, userId, approved, reason);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable String id) {
        ticketService.suspend(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(@PathVariable String id) {
        ticketService.resume(id);
        return ApiResponse.ok();
    }

    /** 动态驳回：跳回指定历史节点（audit1 / revise / pay）；reason 为驳回理由（选填） */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id,
                                    @RequestParam String target,
                                    @RequestParam(required = false) String reason) {
        ticketService.rejectDynamic(id, target, reason);
        return ApiResponse.ok();
    }

    /** 待办任务（按候选人组或处理人查询） */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskVO>> tasks(@RequestParam(required = false) String group,
                                           @RequestParam(required = false) String assignee) {
        return ApiResponse.ok(ticketService.listTasks(group, assignee));
    }
}
