package com.example.jevvsllm.config;

/** Top-category probability must be strictly above this value to skip the LLM. */
public record RoutingPolicy(double confidenceThreshold) {
}
