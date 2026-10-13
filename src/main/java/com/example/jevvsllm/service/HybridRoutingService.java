package com.example.jevvsllm.service;

import com.example.jevvsllm.client.LlmClient;
import com.example.jevvsllm.client.StructuredOutputException;
import com.example.jevvsllm.config.RoutingPolicy;
import com.example.jevvsllm.domain.DecisionPath;
import com.example.jevvsllm.domain.DecisionResult;
import com.example.jevvsllm.domain.ModelSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class HybridRoutingService {

    private final JevDecisionService jevDecisionService;
    private final LlmClient llmClient;
    private final RoutingPolicy policy;

    public HybridRoutingService(
            JevDecisionService jevDecisionService,
            LlmClient llmClient,
            RoutingPolicy policy
    ) {
        this.jevDecisionService = jevDecisionService;
        this.llmClient = llmClient;
        this.policy = policy;
    }

    public DecisionResult route(String ticketText) {
        return route(ticketText, true);
    }

    public DecisionResult route(String ticketText, boolean writeExplanation) {
        JevDecisionService.requireText(ticketText);
        long started = System.nanoTime();
        DecisionResult jev = jevDecisionService.classify(ticketText);
        if (jev.confidence() != null && jev.confidence() > policy.confidenceThreshold()) {
            return new DecisionResult(
                    DecisionPath.HYBRID,
                    ModelSource.JEV,
                    jev.category(),
                    jev.urgency(),
                    jev.confidence(),
                    JevDecisionService.elapsed(started),
                    jev.estimatedCost(),
                    null,
                    jev.categoryProbabilities(),
                    0,
                    0
            );
        }
        try {
            LlmClient.LlmClassification classification = llmClient.classify(ticketText);
            String reasoning = classification.reasoning();
            BigDecimal cost = jev.estimatedCost().add(classification.estimatedCost());
            int llmCalls = classification.llmCallCount();
            if (writeExplanation) {
                LlmClient.LlmSummary summary = llmClient.summarize(
                        ticketText,
                        classification.category(),
                        classification.urgency()
                );
                if (!summary.text().isBlank()) {
                    reasoning = summary.text();
                }
                cost = cost.add(summary.estimatedCost());
                llmCalls += summary.llmCallCount();
            }
            return new DecisionResult(
                    DecisionPath.HYBRID,
                    ModelSource.LLM,
                    classification.category(),
                    classification.urgency(),
                    jev.confidence(),
                    JevDecisionService.elapsed(started),
                    cost,
                    reasoning,
                    jev.categoryProbabilities(),
                    classification.structuredOutputErrors(),
                    llmCalls
            );
        } catch (StructuredOutputException ex) {
            throw new StructuredOutputException(
                    ex.getMessage(),
                    ex.getStructuredOutputErrors(),
                    ex.getLlmCallCount(),
                    JevDecisionService.elapsed(started),
                    jev.estimatedCost().add(ex.getEstimatedCost())
            );
        }
    }
}
