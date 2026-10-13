package com.example.jevvsllm.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Typed decision shared by Jev, the LLM, and the cascade so the three paths
 * can be compared field for field.
 */
public record DecisionResult(
        DecisionPath path,
        ModelSource resolvedBy,
        TicketCategory category,
        TicketUrgency urgency,
        Double confidence,
        long latencyMs,
        BigDecimal estimatedCost,
        String reasoning,
        Map<String, Double> categoryProbabilities,
        int structuredOutputErrors,
        int llmCallCount
) {
}
