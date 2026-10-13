package com.example.jevvsllm.domain;

import java.math.BigDecimal;

/**
 * One path's score on the labeled set.
 * {@code structuredOutputErrorRate} is malformed LLM responses divided by LLM HTTP attempts.
 * {@code jevOnlyFraction} and {@code llmCallsAvoided} are set only for the hybrid path.
 */
public record PathMetrics(
        DecisionPath path,
        int sampleCount,
        double categoryAccuracy,
        double urgencyAccuracy,
        double combinedAccuracy,
        long p50LatencyMs,
        long p95LatencyMs,
        BigDecimal costPerDecision,
        double structuredOutputErrorRate,
        int llmCallCount,
        Double jevOnlyFraction,
        Integer llmCallsAvoided
) {
}
