package com.example.jevvsllm.controller;

import com.example.jevvsllm.domain.BenchmarkReport;
import com.example.jevvsllm.service.BenchmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BenchmarkReport run(
            @RequestParam(required = false) Integer limit,
            @RequestBody(required = false) BenchmarkRunRequest request
    ) {
        Integer bodyLimit = request == null ? null : request.limit();
        return benchmarkService.start(limit != null ? limit : bodyLimit);
    }

    @GetMapping("/results")
    public BenchmarkReport results() {
        return benchmarkService.latest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No benchmark results"));
    }

    public record BenchmarkRunRequest(Integer limit) {
    }
}
