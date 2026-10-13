package com.example.jevvsllm.controller;

import com.example.jevvsllm.domain.StoredTicket;
import com.example.jevvsllm.service.TicketAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketAuditService ticketAuditService;

    public TicketController(TicketAuditService ticketAuditService) {
        this.ticketAuditService = ticketAuditService;
    }

    @PostMapping
    public StoredTicket create(@Valid @RequestBody TicketRequest request) {
        return ticketAuditService.routeAndStore(request.text());
    }

    @GetMapping
    public List<StoredTicket> list() {
        return ticketAuditService.list();
    }

    public record TicketRequest(@NotBlank String text) {
    }
}
