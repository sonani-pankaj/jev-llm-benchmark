package com.example.jevvsllm.domain;

import java.time.Instant;
import java.util.List;

public record BenchmarkReport(
        Long id,
        Instant startedAt,
        Instant completedAt,
        String status,
        String progress,
        String error,
        List<PathMetrics> paths
) {
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
}
