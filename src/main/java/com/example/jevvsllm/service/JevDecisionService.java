package com.example.jevvsllm.service;

import com.example.jevvsllm.client.JevClient;
import com.example.jevvsllm.config.JevProperties;
import com.example.jevvsllm.domain.DecisionPath;
import com.example.jevvsllm.domain.DecisionResult;
import com.example.jevvsllm.domain.ModelSource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class JevDecisionService {

    private final JevClient jevClient;
    private final JevProperties properties;
    private final CostCalculator costs;

    public JevDecisionService(JevClient jevClient, JevProperties properties, CostCalculator costs) {
        this.jevClient = jevClient;
        this.properties = properties;
        this.costs = costs;
    }

    public DecisionResult classify(String ticketText) {
        requireText(ticketText);
        long started = System.nanoTime();
        JevClient.JevOutcome outcome = jevClient.decide(ticketText);
        return new DecisionResult(
                DecisionPath.JEV,
                ModelSource.JEV,
                outcome.category(),
                outcome.urgency(),
                outcome.topCategoryProbability(),
                elapsed(started),
                costs.estimate(
                        outcome.inputTokens(),
                        outcome.outputTokens(),
                        properties.getPricing().getInputPerMillionUsd(),
                        properties.getPricing().getOutputPerMillionUsd()
                ),
                null,
                outcome.categoryProbabilities(),
                0,
                0
        );
    }

    static void requireText(String ticketText) {
        if (ticketText == null || ticketText.isBlank()) {
            throw new IllegalArgumentException("Ticket text is required");
        }
    }

    static long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    static Map<String, Double> emptyProbabilities() {
        return Map.of();
    }
}
