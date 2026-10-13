package com.example.jevvsllm.client;

import com.example.jevvsllm.config.JevClientConfig;
import com.example.jevvsllm.config.JevProperties;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import com.example.jevvsllm.json.Jsons;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JevClient {

    private final RestClient restClient;
    private final JevProperties properties;
    private final Jsons jsons;
    private final LocalJevEngine localEngine;
    private final Map<String, String> categoryCriteria;
    private final String urgencyInstructions;
    private final List<String> urgencyLevels;

    public JevClient(
            @Qualifier(JevClientConfig.REST_CLIENT) RestClient restClient,
            JevProperties properties,
            Jsons jsons
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsons = jsons;
        this.localEngine = new LocalJevEngine();
        this.categoryCriteria = categoryCriteria();
        JsonNode urgencySchema = jsons.readTree(ClasspathText.read("schemas/urgency-scoring-schema.json"));
        this.urgencyInstructions = urgencySchema.path("instructions").asString();
        this.urgencyLevels = new ArrayList<>();
        for (JsonNode level : urgencySchema.path("levels")) {
            urgencyLevels.add(level.asString());
        }
        if (urgencyLevels.size() != TicketUrgency.values().length) {
            throw new IllegalStateException("Urgency score levels must match TicketUrgency");
        }
    }

    public JevOutcome decide(String ticketText) {
        if (localMode()) {
            return localEngine.decide(ticketText);
        }
        requireKey();
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "choice");
        category.put("instructions", "Which team should handle this support ticket?");
        category.put("criteria", categoryCriteria);

        Map<String, Object> urgency = new LinkedHashMap<>();
        urgency.put("type", "score");
        urgency.put("instructions", urgencyInstructions);
        urgency.put("criteria", urgencyLevels);

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("category", category);
        questions.put("urgency", urgency);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getApi().getModel());
        body.put("state", ticketText);
        body.put("questions", questions);

        String response = restClient.post()
                .uri(properties.getApi().getUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApi().getKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsons.writeBytes(body))
                .retrieve()
                .body(String.class);

        return parse(response);
    }

    private JevOutcome parse(String response) {
        JsonNode root = jsons.readTree(response);
        JsonNode answers = root.path("answers");
        JsonNode category = answers.path("category");
        JsonNode urgency = answers.path("urgency");
        Map<String, Double> probabilities = probabilities(category.path("probabilities"));
        if (probabilities.isEmpty()) {
            throw new IllegalStateException("Jev category response did not include probabilities");
        }
        String choice = category.path("choice").asString("");
        TicketCategory parsedCategory;
        try {
            parsedCategory = TicketCategory.valueOf(choice);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Jev returned a category outside the schema: " + choice);
        }
        double top = probabilities.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        TicketUrgency parsedUrgency = TicketUrgency.fromScore(urgency.path("score").asDouble());
        JsonNode usage = root.path("usage");
        return new JevOutcome(
                parsedCategory,
                parsedUrgency,
                top,
                Map.copyOf(probabilities),
                usage.path("input_tokens").asLong(0),
                usage.path("output_tokens").asLong(0)
        );
    }

    private static Map<String, Double> probabilities(JsonNode node) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (!node.isObject()) {
            return values;
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            values.put(entry.getKey(), entry.getValue().asDouble());
        }
        return values;
    }

    private boolean localMode() {
        String mode = properties.getApi().getMode();
        return mode == null || mode.isBlank() || "local".equalsIgnoreCase(mode.trim());
    }

    private void requireKey() {
        if (properties.getApi().getKey() == null || properties.getApi().getKey().isBlank()) {
            throw new MissingApiKeyException(
                    "Jev API key is missing. Set TYPESAFE_API_KEY, or keep jev.api.mode=local for the free stand-in."
            );
        }
    }

    private static Map<String, String> categoryCriteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("BILLING", "Charges, invoices, refunds, payments, and receipts");
        criteria.put("BUG", "Broken product behavior, errors, and outages");
        criteria.put("ACCOUNT", "Login, profile, access, and account settings");
        criteria.put("SALES", "Pricing, upgrades, purchases, and new business");
        return Map.copyOf(criteria);
    }

    public record JevOutcome(
            TicketCategory category,
            TicketUrgency urgency,
            double topCategoryProbability,
            Map<String, Double> categoryProbabilities,
            long inputTokens,
            long outputTokens
    ) {
    }
}
