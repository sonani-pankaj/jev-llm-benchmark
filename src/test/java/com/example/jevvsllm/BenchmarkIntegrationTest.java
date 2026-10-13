package com.example.jevvsllm;

import com.example.jevvsllm.domain.DecisionPath;
import com.example.jevvsllm.domain.ModelSource;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import com.example.jevvsllm.json.Jsons;
import com.example.jevvsllm.service.JevDecisionService;
import com.example.jevvsllm.service.LlmDecisionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BenchmarkIntegrationTest {

    private static final ApiStub STUB = ApiStub.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jev.api.mode", () -> "remote");
        registry.add("jev.api.url", STUB::jevUrl);
        registry.add("llm.api.url", STUB::llmUrl);
        registry.add("jev.api.key", () -> "test-jev-key");
        registry.add("llm.api.key", () -> "test-llm-key");
        registry.add("benchmark.dataset", () -> "classpath:dataset/test-tickets.json");
        registry.add("benchmark.default-limit", () -> "0");
        registry.add("llm.api.max-attempts", () -> "3");
        registry.add("routing.confidence-threshold", () -> "0.85");
        registry.add("jev.pricing.input-per-million-usd", () -> "0.042");
        registry.add("jev.pricing.output-per-million-usd", () -> "0");
        registry.add("llm.pricing.input-per-million-usd", () -> "1");
        registry.add("llm.pricing.output-per-million-usd", () -> "1");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @LocalServerPort
    int port;

    @Autowired
    Jsons jsons;

    @Autowired
    JevDecisionService jevDecisionService;

    @Autowired
    LlmDecisionService llmDecisionService;

    @Test
    void bothPathsEmitOnlyTypedLabels() {
        var jev = jevDecisionService.classify("Duplicate invoice charge. category=BILLING urgency=HIGH");
        var llm = llmDecisionService.classify("Export returns 500. category=BUG urgency=MEDIUM");

        assertThat(jev.category()).isEqualTo(TicketCategory.BILLING);
        assertThat(jev.urgency()).isEqualTo(TicketUrgency.HIGH);
        assertThat(jev.path()).isEqualTo(DecisionPath.JEV);
        assertThat(llm.category()).isEqualTo(TicketCategory.BUG);
        assertThat(llm.urgency()).isEqualTo(TicketUrgency.MEDIUM);
        assertThat(llm.path()).isEqualTo(DecisionPath.LLM);
        assertThat(TicketCategory.valueOf(jev.category().name())).isNotNull();
        assertThat(TicketUrgency.valueOf(llm.urgency().name())).isNotNull();
    }

    @Test
    void ticketEndpointStoresCascadeDecision() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        String clear = client.post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsons.writeBytes(java.util.Map.of("text", "Duplicate invoice charge. category=BILLING urgency=HIGH")))
                .retrieve()
                .body(String.class);
        JsonNode clearNode = jsons.readTree(clear);
        assertThat(TicketCategory.valueOf(clearNode.path("category").asString())).isEqualTo(TicketCategory.BILLING);
        assertThat(TicketUrgency.valueOf(clearNode.path("urgency").asString())).isEqualTo(TicketUrgency.HIGH);
        assertThat(clearNode.path("path").asString()).isEqualTo(DecisionPath.HYBRID.name());
        assertThat(clearNode.path("resolvedBy").asString()).isEqualTo(ModelSource.JEV.name());

        String ambiguous = client.post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsons.writeBytes(java.util.Map.of("text", "ambiguous invoice and a crash. category=BILLING urgency=MEDIUM")))
                .retrieve()
                .body(String.class);
        JsonNode ambiguousNode = jsons.readTree(ambiguous);
        assertThat(ambiguousNode.path("resolvedBy").asString()).isEqualTo(ModelSource.LLM.name());
        assertThat(ambiguousNode.path("reasoning").asString()).isNotBlank();
        assertThat(TicketCategory.valueOf(ambiguousNode.path("category").asString())).isEqualTo(TicketCategory.BILLING);

        String listed = client.get().uri("/api/tickets").retrieve().body(String.class);
        assertThat(jsons.readTree(listed).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void benchmarkReportsAccuracyLatencyCostAndCascadeSavings() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        ResponseEntity<String> response = client.post()
                .uri("/api/benchmark/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsons.writeBytes(java.util.Map.of()))
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode accepted = jsons.readTree(response.getBody());
        assertThat(accepted.path("status").asString()).isEqualTo("RUNNING");
        JsonNode report = waitUntilComplete(client);
        JsonNode paths = report.path("paths");
        assertThat(paths.size()).isEqualTo(3);

        JsonNode jev = path(paths, "JEV");
        JsonNode llm = path(paths, "LLM");
        JsonNode hybrid = path(paths, "HYBRID");
        for (JsonNode metrics : new JsonNode[] {jev, llm, hybrid}) {
            assertThat(metrics.path("categoryAccuracy").asDouble()).isBetween(0.0, 1.0);
            assertThat(metrics.path("p50LatencyMs").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(metrics.path("p95LatencyMs").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(new BigDecimal(metrics.path("costPerDecision").asString())).isGreaterThan(BigDecimal.ZERO);
        }
        assertThat(llm.path("structuredOutputErrorRate").asDouble()).isGreaterThan(0.0);
        assertThat(hybrid.path("llmCallCount").asInt()).isLessThan(llm.path("llmCallCount").asInt());
        assertThat(hybrid.path("jevOnlyFraction").asDouble()).isBetween(0.0, 1.0);
        assertThat(hybrid.path("llmCallsAvoided").asInt()).isPositive();

        String stored = client.get().uri("/api/benchmark/results").retrieve().body(String.class);
        assertThat(jsons.readTree(stored).path("id").asLong()).isEqualTo(report.path("id").asLong());
    }

    private JsonNode waitUntilComplete(RestClient client) {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode report = jsons.readTree("{}");
        while (System.currentTimeMillis() < deadline) {
            String body = client.get().uri("/api/benchmark/results").retrieve().body(String.class);
            report = jsons.readTree(body);
            if ("COMPLETED".equals(report.path("status").asString())) {
                return report;
            }
            if ("FAILED".equals(report.path("status").asString())) {
                throw new AssertionError("Benchmark failed: " + report.path("error").asString());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for benchmark");
            }
        }
        throw new AssertionError("Benchmark did not finish: " + report);
    }

    private static JsonNode path(JsonNode paths, String name) {
        for (JsonNode path : paths) {
            if (name.equals(path.path("path").asString())) {
                return path;
            }
        }
        throw new AssertionError("Missing path " + name);
    }

    static final class ApiStub {
        private static final Pattern CATEGORY = Pattern.compile("category=([A-Z]+)");
        private static final Pattern URGENCY = Pattern.compile("urgency=([A-Z]+)");

        private final HttpServer server;
        private final AtomicInteger retryOnceCalls = new AtomicInteger();

        private ApiStub(HttpServer server) {
            this.server = server;
        }

        static ApiStub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ApiStub stub = new ApiStub(server);
                server.createContext("/jev", exchange -> stub.respond(exchange, stub.jev(body(exchange))));
                server.createContext("/chat", exchange -> stub.respond(exchange, stub.llm(body(exchange))));
                server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "api-stub");
                    thread.setDaemon(true);
                    return thread;
                }));
                server.start();
                return stub;
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to start API stub", ex);
            }
        }

        String jevUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/jev";
        }

        String llmUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        }

        void stop() {
            server.stop(0);
        }

        private String jev(String request) {
            String state = textField(request, "state");
            boolean ambiguous = state.contains("ambiguous");
            String category = ambiguous ? "SALES" : match(CATEGORY, state, "BILLING");
            String urgency = match(URGENCY, state, "LOW");
            int score = TicketUrgency.valueOf(urgency).ordinal();
            double top = ambiguous ? 0.40 : 0.96;
            String probabilities = ambiguous
                    ? "{\"SALES\":0.40,\"BILLING\":0.25,\"BUG\":0.20,\"ACCOUNT\":0.15}"
                    : peaked(category);
            return """
                    {
                      "model": "jev-1.13.0",
                      "answers": {
                        "category": {
                          "type": "choice",
                          "choice": "%s",
                          "confidence": %s,
                          "probabilities": %s
                        },
                        "urgency": {
                          "type": "score",
                          "score": %d,
                          "confidence": 0.99,
                          "probabilities": {"0": 0.01, "1": 0.01, "2": 0.01, "3": 0.97}
                        }
                      },
                      "usage": {"input_tokens": 200, "output_tokens": 40}
                    }
                    """.formatted(category, ambiguous ? "0.20" : "0.99", probabilities, score);
        }

        private String llm(String request) {
            if (request.contains("\"tools\"") && request.contains("retry-once")) {
                if (retryOnceCalls.incrementAndGet() % 2 == 1) {
                    return """
                            {
                              "choices": [{
                                "message": {
                                  "tool_calls": [{
                                    "id": "call_bad",
                                    "type": "function",
                                    "function": {"name": "classifyTicket", "arguments": "not-json"}
                                  }]
                                }
                              }],
                              "usage": {"prompt_tokens": 30, "completion_tokens": 5}
                            }
                            """;
                }
            }
            if (!request.contains("\"tools\"")) {
                return """
                        {
                          "choices": [{"message": {"content": "Escalated because the category was split across queues."}}],
                          "usage": {"prompt_tokens": 40, "completion_tokens": 12}
                        }
                        """;
            }
            String content = request;
            String category = match(CATEGORY, content, "BILLING");
            String urgency = match(URGENCY, content, "LOW");
            return """
                    {
                      "choices": [{
                        "message": {
                          "tool_calls": [{
                            "id": "call_ok",
                            "type": "function",
                            "function": {
                              "name": "classifyTicket",
                              "arguments": "{\\"category\\":\\"%s\\",\\"urgency\\":\\"%s\\",\\"reasoning\\":\\"matched labels\\"}"
                            }
                          }]
                        }
                      }],
                      "usage": {"prompt_tokens": 80, "completion_tokens": 20}
                    }
                    """.formatted(category, urgency);
        }

        private void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private static String body(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        private static String textField(String json, String field) {
            Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                return "";
            }
            return matcher.group(1);
        }

        private static String match(Pattern pattern, String text, String fallback) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) {
                return fallback;
            }
            return matcher.group(1);
        }

        private static String peaked(String category) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (TicketCategory value : TicketCategory.values()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                double probability = value.name().equals(category) ? 0.96 : 0.013333;
                if (value == TicketCategory.SALES && !category.equals("SALES")) {
                    probability = 0.013334;
                }
                builder.append('"').append(value.name()).append("\":").append(probability);
            }
            builder.append('}');
            return builder.toString();
        }
    }
}
