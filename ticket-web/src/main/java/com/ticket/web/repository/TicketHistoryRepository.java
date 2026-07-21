package com.ticket.web.repository;

import com.ticket.common.domain.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, String> {

    List<TicketHistory> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
