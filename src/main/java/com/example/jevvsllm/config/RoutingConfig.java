package com.example.jevvsllm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cascade policy: Jev first, LLM only when the top category probability does not
 * clear the threshold.
 * <p>
 * Add a Jev question when a new judgment is a bounded decision you want on every
 * ticket: classification, urgency, or a yes/no gate. Jev returns a probability
 * the cascade can threshold, and the extra question shares the same round trip.
 * Add an LLM prompt when the ticket needs language a probability cannot carry:
 * a written explanation, a summary for a person, or a nested extraction. That
 * prompt should run only after the cascade escalates, so the expensive call
 * stays on the ambiguous minority.
 */
@Configuration
public class RoutingConfig {

    @Bean
    public RoutingPolicy routingPolicy(RoutingProperties properties) {
        double threshold = properties.getConfidenceThreshold();
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalStateException("routing.confidence-threshold must be between 0 and 1");
        }
        return new RoutingPolicy(threshold);
    }
}
