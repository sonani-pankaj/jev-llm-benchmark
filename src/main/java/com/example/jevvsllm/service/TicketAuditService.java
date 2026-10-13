package com.example.jevvsllm.service;

import com.example.jevvsllm.domain.DecisionResult;
import com.example.jevvsllm.domain.StoredTicket;
import com.example.jevvsllm.domain.SupportTicket;
import com.example.jevvsllm.json.Jsons;
import com.example.jevvsllm.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketAuditService {

    private final TicketRepository ticketRepository;
    private final HybridRoutingService hybridRoutingService;
    private final Jsons jsons;

    public TicketAuditService(
            TicketRepository ticketRepository,
            HybridRoutingService hybridRoutingService,
            Jsons jsons
    ) {
        this.ticketRepository = ticketRepository;
        this.hybridRoutingService = hybridRoutingService;
        this.jsons = jsons;
    }

    public StoredTicket routeAndStore(String text) {
        DecisionResult decision = hybridRoutingService.route(text);
        SupportTicket ticket = SupportTicket.from(text, decision, jsons.write(decision.categoryProbabilities()));
        return ticketRepository.save(ticket).toStoredTicket();
    }

    @Transactional(readOnly = true)
    public List<StoredTicket> list() {
        return ticketRepository.findAll().stream().map(SupportTicket::toStoredTicket).toList();
    }
}
