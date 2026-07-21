package com.ticket.common.spi;

import com.ticket.common.dto.CreateTicketRequest;
import com.ticket.common.dto.RouteResult;
import com.ticket.common.dto.TaskView;
import com.ticket.common.dto.TicketVO;

import java.util.List;

/**
 * 工单命令/查询接口（SPI）。
 *
 * <p>把 Agent 编排层真正需要调用的几个工单操作抽成接口，放在最底层的 common 模块，
 * 由 web 层的 {@code TicketService} 实现。这样 ticket-agent 只需依赖 common / ai / workflow，
 * 而 web 再依赖 agent 把其类打进可执行 jar——形成单向依赖、无循环。</p>
 *
 * <p>所有方法签名只用 common 内的类型（TicketVO / CreateTicketRequest / RouteResult / TaskView），
 * 不引用 web 层类型，保证 agent 模块可独立编译。</p>
 */
public interface TicketCommandApi {

    /** 创建工单（含 AI 语义分派 / 人在回路） */
    TicketVO createTicket(CreateTicketRequest req);

    /** 按 id 查询工单 */
    TicketVO getTicket(String id);

    /** 列表查询工单（可按状态 / 分类 / 是否待复核过滤） */
    List<TicketVO> listTickets(String status, String category, Boolean review);

    /** 待办任务视图（Agent 用来定位“派单”所需的 taskId） */
    List<TaskView> listTaskViews(String group, String assignee);

    /** 接单 / 分派（认领任务给某人） */
    void claim(String ticketId, String taskId, String userId);

    /** 动态驳回 / 升级到指定节点（activityId：audit1 / revise / pay / audit2）；reason 为驳回理由（可为空） */
    void rejectDynamic(String ticketId, String targetActivityId, String reason);

    /** 仅做语义分派（不建单），供 Agent 理解“这条工单属于哪类” */
    RouteResult routeOnly(String title, String description);
}
