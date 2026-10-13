package com.example.jevvsllm.client;

import com.example.jevvsllm.config.LlmClientConfig;
import com.example.jevvsllm.config.LlmProperties;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import com.example.jevvsllm.json.Jsons;
import com.example.jevvsllm.service.CostCalculator;
import com.example.jevvsllm.service.PromptCatalog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    static final String TOOL_NAME = "classifyTicket";

    private final RestClient restClient;
    private final LlmProperties properties;
    private final Jsons jsons;
    private final PromptCatalog prompts;
    private final CostCalculator costs;
    private final Object toolParameters;

    public LlmClient(
            @Qualifier(LlmClientConfig.REST_CLIENT) RestClient restClient,
            LlmProperties properties,
            Jsons jsons,
            PromptCatalog prompts,
            CostCalculator costs
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsons = jsons;
        this.prompts = prompts;
        this.costs = costs;
        JsonNode schema = jsons.readTree(ClasspathText.read("schemas/ticket-routing-schema.json"));
        this.toolParameters = jsons.toJava(schema);
    }

    public LlmClassification classify(String ticketText) {
        String prompt = prompts.classification(ticketText);
        int errors = 0;
        int calls = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long started = System.nanoTime();
        int maxAttempts = properties.getApi().getMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            calls++;
            String response = post(prompt, true);
            TokenUsage usage = usage(response);
            inputTokens += usage.inputTokens();
            outputTokens += usage.outputTokens();
            try {
                ParsedTool parsed = parseToolCall(response);
                return new LlmClassification(
                        parsed.category(),
                        parsed.urgency(),
                        parsed.reasoning(),
                        elapsed(started),
                        cost(inputTokens, outputTokens),
                        errors,
                        calls
                );
            } catch (MalformedToolException ex) {
                errors++;
            }
        }
        throw new StructuredOutputException(
                "LLM structured output failed after " + maxAttempts + " attempts",
                errors,
                calls,
                elapsed(started),
                cost(inputTokens, outputTokens)
        );
    }

    public LlmSummary summarize(String ticketText, TicketCategory category, TicketUrgency urgency) {
        long started = System.nanoTime();
        String response = post(prompts.escalation(ticketText, category, urgency), false);
        TokenUsage usage = usage(response);
        JsonNode content = jsons.readTree(response).path("choices").path(0).path("message").path("content");
        String text = content.isMissingNode() || content.isNull() ? "" : content.asString("");
        return new LlmSummary(text, elapsed(started), cost(usage.inputTokens(), usage.outputTokens()), 1);
    }

    private String post(String prompt, boolean withTool) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getApi().getModel());
        body.put("stream", false);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        if (withTool) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", TOOL_NAME);
            function.put("description", "Classify a support ticket into a category and urgency");
            function.put("parameters", toolParameters);
            body.put("tools", List.of(Map.of("type", "function", "function", function)));
            body.put("tool_choice", Map.of("type", "function", "function", Map.of("name", TOOL_NAME)));
        }
        var request = restClient.post()
                .uri(properties.getApi().getUrl())
                .contentType(MediaType.APPLICATION_JSON);
        String key = properties.getApi().getKey();
        if (key != null && !key.isBlank()) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        }
        return request
                .body(jsons.writeBytes(body))
                .retrieve()
                .body(String.class);
    }

    private ParsedTool parseToolCall(String response) {
        JsonNode message = jsons.readTree(response).path("choices").path(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            JsonNode arguments = null;
            for (JsonNode call : toolCalls) {
                JsonNode function = call.path("function");
                if (TOOL_NAME.equals(function.path("name").asString())) {
                    arguments = function.path("arguments");
                    break;
                }
            }
            if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
                throw new MalformedToolException("classifyTicket arguments missing");
            }
            return parseArguments(arguments);
        }
        JsonNode content = message.path("content");
        if (content.isMissingNode() || content.isNull() || content.asString("").isBlank()) {
            throw new MalformedToolException("missing tool call");
        }
        return parseArguments(content);
    }

    /**
     * Accepts an OpenAI tool-call arguments node, or the JSON text Ollama's
     * compatible endpoint puts in message content:
     * {"name":"classifyTicket","arguments":{...}}.
     */
    private ParsedTool parseArguments(JsonNode arguments) {
        JsonNode payload = objectPayload(arguments);
        if (payload.has("arguments")) {
            payload = objectPayload(payload.path("arguments"));
        }
        if (!payload.hasNonNull("category") || !payload.hasNonNull("urgency") || !payload.has("reasoning")) {
            throw new MalformedToolException("tool arguments are missing required fields");
        }
        try {
            return new ParsedTool(
                    TicketCategory.valueOf(payload.path("category").asString()),
                    TicketUrgency.valueOf(payload.path("urgency").asString()),
                    payload.path("reasoning").asString("")
            );
        } catch (IllegalArgumentException ex) {
            throw new MalformedToolException("tool arguments used a value outside the schema");
        }
    }

    private JsonNode objectPayload(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new MalformedToolException("tool arguments are not JSON");
        }
        if (node.isString()) {
            try {
                return jsons.readTree(stripFences(node.asString()));
            } catch (RuntimeException ex) {
                throw new MalformedToolException("tool arguments are not JSON");
            }
        }
        return node;
    }

    private static String stripFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline >= 0) {
                trimmed = trimmed.substring(newline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private TokenUsage usage(String response) {
        JsonNode usage = jsons.readTree(response).path("usage");
        return new TokenUsage(usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    }

    private BigDecimal cost(long inputTokens, long outputTokens) {
        return costs.estimate(
                inputTokens,
                outputTokens,
                properties.getPricing().getInputPerMillionUsd(),
                properties.getPricing().getOutputPerMillionUsd()
        );
    }

    private static long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public record LlmClassification(
            TicketCategory category,
            TicketUrgency urgency,
            String reasoning,
            long latencyMs,
            BigDecimal estimatedCost,
            int structuredOutputErrors,
            int llmCallCount
    ) {
    }

    public record LlmSummary(String text, long latencyMs, BigDecimal estimatedCost, int llmCallCount) {
    }

    private record TokenUsage(long inputTokens, long outputTokens) {
    }

    private record ParsedTool(TicketCategory category, TicketUrgency urgency, String reasoning) {
    }

    private static final class MalformedToolException extends RuntimeException {
        private MalformedToolException(String message) {
            super(message);
        }
    }
}
