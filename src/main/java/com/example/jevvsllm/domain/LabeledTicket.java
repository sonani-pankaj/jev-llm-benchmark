package com.example.jevvsllm.domain;

public record LabeledTicket(
        String id,
        String text,
        TicketCategory category,
        TicketUrgency urgency
) {
}
