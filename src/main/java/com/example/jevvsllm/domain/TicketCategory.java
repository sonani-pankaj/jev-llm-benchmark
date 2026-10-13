package com.example.jevvsllm.domain;

/**
 * Bounded queues both models classify into. These names are the Jev choice keys
 * and the LLM tool enum, so a new queue has to be added in both places together.
 */
public enum TicketCategory {
    BILLING,
    BUG,
    ACCOUNT,
    SALES
}
