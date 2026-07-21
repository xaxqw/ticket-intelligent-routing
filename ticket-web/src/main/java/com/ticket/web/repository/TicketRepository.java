package com.ticket.web.repository;

import com.ticket.common.domain.Ticket;
import com.ticket.common.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    @Query("select t from Ticket t where t.idempotencyKey = ?1")
    Optional<Ticket> findByIdempotencyKey(String idempotencyKey);

    Optional<Ticket> findByProcessInstanceId(String processInstanceId);

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByCategory(com.ticket.common.domain.Category category);

    List<Ticket> findByAiReviewRequiredTrue();

    List<Ticket> findAllByOrderByCreatedAtDesc();
}
