package com.ticket.web.service;

import com.ticket.common.constants.TicketConstants;
import com.ticket.common.domain.Ticket;
import com.ticket.common.domain.TicketHistory;
import com.ticket.common.domain.TicketStatus;
import com.ticket.common.util.SnowflakeId;
import com.ticket.web.repository.TicketHistoryRepository;
import com.ticket.web.repository.TicketRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * “归档”服务任务的委托。流程走到归档节点时由 Flowable 调用，
 * 将工单状态置为已完成并写审计。这样状态机闭环由流程驱动，业务与引擎强一致。
 */
@Component("ticketCompletionHandler")
public class TicketCompletionHandler implements JavaDelegate {

    @Resource
    private TicketRepository ticketRepository;
    @Resource
    private TicketHistoryRepository historyRepository;
    @Resource
    private SnowflakeId snowflakeId;

    @Override
    public void execute(DelegateExecution execution) {
        Object tid = execution.getVariable(TicketConstants.VAR_TICKET_ID);
        if (tid == null) return;
        Ticket ticket = ticketRepository.findById(tid.toString()).orElse(null);
        if (ticket == null) return;

        if (ticket.getStatus() != TicketStatus.COMPLETED) {
            ticket.setStatus(TicketStatus.COMPLETED);
            ticket.setCompletedAt(LocalDateTime.now());
            ticket.setUpdatedAt(LocalDateTime.now());
            ticketRepository.save(ticket);

            historyRepository.save(new TicketHistory(
                    snowflakeId.nextStr(), ticket.getId(),
                    TicketConstants.NODE_ARCHIVE, "system", TicketConstants.ACTION_ARCHIVE));
        }
    }
}
