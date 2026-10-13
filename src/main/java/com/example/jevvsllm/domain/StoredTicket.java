package com.example.jevvsllm.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Audit row for a ticket routed through the cascade. */
public record StoredTicket(
        Long id,
        String text,
        TicketCategory category,
        TicketUrgency urgency,
        Double confidence,
        DecisionPath path,
        ModelSource resolvedBy,
        long latencyMs,
        BigDecimal estimatedCost,
        String reasoning,
        Instant createdAt
) {
}
