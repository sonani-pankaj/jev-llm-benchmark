package com.example.jevvsllm.repository;

import com.example.jevvsllm.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<SupportTicket, Long> {
}
