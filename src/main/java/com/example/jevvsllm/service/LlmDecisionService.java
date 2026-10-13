package com.example.jevvsllm.service;

import com.example.jevvsllm.client.LlmClient;
import com.example.jevvsllm.domain.DecisionPath;
import com.example.jevvsllm.domain.DecisionResult;
import com.example.jevvsllm.domain.ModelSource;
import org.springframework.stereotype.Service;

@Service
public class LlmDecisionService {

    private final LlmClient llmClient;

    public LlmDecisionService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public DecisionResult classify(String ticketText) {
        JevDecisionService.requireText(ticketText);
        LlmClient.LlmClassification classification = llmClient.classify(ticketText);
        return new DecisionResult(
                DecisionPath.LLM,
                ModelSource.LLM,
                classification.category(),
                classification.urgency(),
                null,
                classification.latencyMs(),
                classification.estimatedCost(),
                classification.reasoning(),
                JevDecisionService.emptyProbabilities(),
                classification.structuredOutputErrors(),
                classification.llmCallCount()
        );
    }
}
