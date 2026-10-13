package com.example.jevvsllm.service;

import com.example.jevvsllm.client.ClasspathText;
import com.example.jevvsllm.client.StructuredOutputException;
import com.example.jevvsllm.config.BenchmarkProperties;
import com.example.jevvsllm.domain.BenchmarkReport;
import com.example.jevvsllm.domain.DecisionPath;
import com.example.jevvsllm.domain.DecisionResult;
import com.example.jevvsllm.domain.LabeledTicket;
import com.example.jevvsllm.domain.ModelSource;
import com.example.jevvsllm.domain.PathMetrics;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import com.example.jevvsllm.json.Jsons;
import com.example.jevvsllm.persistence.BenchmarkRunEntity;
import com.example.jevvsllm.repository.BenchmarkRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final JevDecisionService jevDecisionService;
    private final LlmDecisionService llmDecisionService;
    private final HybridRoutingService hybridRoutingService;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkProperties properties;
    private final Jsons jsons;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(thread -> {
        Thread worker = new Thread(thread, "benchmark-run");
        worker.setDaemon(true);
        return worker;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BenchmarkService(
            JevDecisionService jevDecisionService,
            LlmDecisionService llmDecisionService,
            HybridRoutingService hybridRoutingService,
            BenchmarkRunRepository benchmarkRunRepository,
            BenchmarkProperties properties,
            Jsons jsons
    ) {
        this.jevDecisionService = jevDecisionService;
        this.llmDecisionService = llmDecisionService;
        this.hybridRoutingService = hybridRoutingService;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.properties = properties;
        this.jsons = jsons;
    }

    public BenchmarkReport start(Integer requestedLimit) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A benchmark is already running. GET /api/benchmark/results for progress.");
        }
        Instant startedAt = Instant.now();
        List<LabeledTicket> samples = loadSamples(requestedLimit);
        BenchmarkRunEntity entity = new BenchmarkRunEntity();
        entity.setStartedAt(startedAt);
        BenchmarkReport queued = new BenchmarkReport(
                null,
                startedAt,
                null,
                BenchmarkReport.RUNNING,
                "Queued " + samples.size() + " tickets",
                null,
                List.of()
        );
        entity.setReportJson(jsons.write(queued));
        entity = benchmarkRunRepository.save(entity);
        BenchmarkReport started = withId(queued, entity.getId());
        persist(entity, started);
        Long runId = entity.getId();
        executor.submit(() -> execute(runId, startedAt, samples));
        return started;
    }

    public Optional<BenchmarkReport> latest() {
        return benchmarkRunRepository.findFirstByOrderByIdDesc()
                .map(entity -> jsons.read(entity.getReportJson(), BenchmarkReport.class));
    }

    private void execute(Long runId, Instant startedAt, List<LabeledTicket> samples) {
        BenchmarkRunEntity entity = benchmarkRunRepository.findById(runId).orElseThrow();
        try {
            progress(entity, startedAt, "JEV 0/" + samples.size());
            PathMetrics jev = evaluate(DecisionPath.JEV, samples, jevDecisionService::classify, null, entity, startedAt);
            progress(entity, startedAt, "LLM 0/" + samples.size());
            PathMetrics llm = evaluate(DecisionPath.LLM, samples, llmDecisionService::classify, null, entity, startedAt);
            progress(entity, startedAt, "HYBRID 0/" + samples.size());
            PathMetrics hybrid = evaluate(
                    DecisionPath.HYBRID,
                    samples,
                    text -> hybridRoutingService.route(text, false),
                    llm.llmCallCount(),
                    entity,
                    startedAt
            );
            BenchmarkReport done = new BenchmarkReport(
                    runId,
                    startedAt,
                    Instant.now(),
                    BenchmarkReport.COMPLETED,
                    "Finished " + samples.size() + " tickets",
                    null,
                    List.of(jev, llm, hybrid)
            );
            persist(entity, done);
            log.info("Benchmark {} completed", runId);
        } catch (Exception ex) {
            log.warn("Benchmark {} failed", runId, ex);
            persist(entity, new BenchmarkReport(
                    runId,
                    startedAt,
                    Instant.now(),
                    BenchmarkReport.FAILED,
                    "Failed",
                    ex.getMessage(),
                    List.of()
            ));
        } finally {
            running.set(false);
        }
    }

    private PathMetrics evaluate(
            DecisionPath path,
            List<LabeledTicket> samples,
            Function<String, DecisionResult> decide,
            Integer llmOnlyCalls,
            BenchmarkRunEntity entity,
            Instant startedAt
    ) {
        int categoryHits = 0;
        int urgencyHits = 0;
        int combinedHits = 0;
        int structuredErrors = 0;
        int llmCalls = 0;
        int resolvedByJev = 0;
        BigDecimal cost = BigDecimal.ZERO;
        List<Long> latencies = new ArrayList<>();
        int index = 0;
        for (LabeledTicket sample : samples) {
            index++;
            progress(entity, startedAt, path.name() + " " + index + "/" + samples.size());
            log.info("Benchmark {} ticket {}/{} ({})", path, index, samples.size(), sample.id());
            try {
                DecisionResult result = decide.apply(sample.text());
                if (result.category() == sample.category()) {
                    categoryHits++;
                }
                if (result.urgency() == sample.urgency()) {
                    urgencyHits++;
                }
                if (result.category() == sample.category() && result.urgency() == sample.urgency()) {
                    combinedHits++;
                }
                structuredErrors += result.structuredOutputErrors();
                llmCalls += result.llmCallCount();
                if (result.resolvedBy() == ModelSource.JEV) {
                    resolvedByJev++;
                }
                cost = cost.add(result.estimatedCost());
                latencies.add(result.latencyMs());
            } catch (StructuredOutputException ex) {
                structuredErrors += ex.getStructuredOutputErrors();
                llmCalls += ex.getLlmCallCount();
                cost = cost.add(ex.getEstimatedCost());
                latencies.add(ex.getLatencyMs());
            } catch (RuntimeException ex) {
                log.warn("Benchmark {} failed on {}: {}", path, sample.id(), ex.getMessage());
                latencies.add(0L);
            }
        }
        int count = samples.size();
        Double jevOnlyFraction = null;
        Integer llmCallsAvoided = null;
        if (path == DecisionPath.HYBRID) {
            jevOnlyFraction = count == 0 ? 0.0 : (double) resolvedByJev / count;
            llmCallsAvoided = llmOnlyCalls - llmCalls;
        }
        return new PathMetrics(
                path,
                count,
                ratio(categoryHits, count),
                ratio(urgencyHits, count),
                ratio(combinedHits, count),
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                count == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP),
                llmCalls == 0 ? 0.0 : (double) structuredErrors / llmCalls,
                llmCalls,
                jevOnlyFraction,
                llmCallsAvoided
        );
    }

    private List<LabeledTicket> loadSamples(Integer requestedLimit) {
        String location = properties.getDataset();
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        JsonNode root = jsons.readTree(ClasspathText.read(path));
        if (!root.isArray()) {
            throw new IllegalStateException("Labeled dataset must be a JSON array");
        }
        List<LabeledTicket> samples = new ArrayList<>();
        for (JsonNode node : root) {
            samples.add(new LabeledTicket(
                    node.path("id").asString(),
                    node.path("text").asString(),
                    TicketCategory.valueOf(node.path("category").asString()),
                    TicketUrgency.valueOf(node.path("urgency").asString())
            ));
        }
        int limit = requestedLimit != null ? requestedLimit : properties.getDefaultLimit();
        if (limit > 0 && limit < samples.size()) {
            return List.copyOf(samples.subList(0, limit));
        }
        return List.copyOf(samples);
    }

    private void progress(BenchmarkRunEntity entity, Instant startedAt, String progress) {
        persist(entity, new BenchmarkReport(
                entity.getId(),
                startedAt,
                null,
                BenchmarkReport.RUNNING,
                progress,
                null,
                List.of()
        ));
    }

    private void persist(BenchmarkRunEntity entity, BenchmarkReport report) {
        entity.setStartedAt(report.startedAt());
        entity.setCompletedAt(report.completedAt());
        entity.setReportJson(jsons.write(report));
        benchmarkRunRepository.save(entity);
    }

    private static BenchmarkReport withId(BenchmarkReport report, Long id) {
        return new BenchmarkReport(
                id,
                report.startedAt(),
                report.completedAt(),
                report.status(),
                report.progress(),
                report.error(),
                report.paths()
        );
    }

    private static double ratio(int hits, int count) {
        if (count == 0) {
            return 0.0;
        }
        return (double) hits / count;
    }

    static long percentile(List<Long> latencies, double p) {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index);
    }
}
