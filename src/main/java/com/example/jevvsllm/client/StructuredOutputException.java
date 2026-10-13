package com.example.jevvsllm.client;

import java.math.BigDecimal;

/** Raised when tool JSON is still invalid after the configured attempts. */
public class StructuredOutputException extends RuntimeException {

    private final int structuredOutputErrors;
    private final int llmCallCount;
    private final long latencyMs;
    private final BigDecimal estimatedCost;

    public StructuredOutputException(
            String message,
            int structuredOutputErrors,
            int llmCallCount,
            long latencyMs,
            BigDecimal estimatedCost
    ) {
        super(message);
        this.structuredOutputErrors = structuredOutputErrors;
        this.llmCallCount = llmCallCount;
        this.latencyMs = latencyMs;
        this.estimatedCost = estimatedCost;
    }

    public int getStructuredOutputErrors() {
        return structuredOutputErrors;
    }

    public int getLlmCallCount() {
        return llmCallCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }
}
