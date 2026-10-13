package com.example.jevvsllm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * LLM calls are for work Jev does not do: free-text explanation and tool calls
 * whose schema is nested or still changing. The default target is a local
 * Ollama server at /v1/chat/completions. Pair every new prompt under
 * resources/prompts with a JSON schema whose fields are all required.
 * Malformed tool JSON is retried; Jev does not need that retry.
 */
@Configuration
public class LlmClientConfig {

    public static final String REST_CLIENT = "llmRestClient";

    @Bean(name = REST_CLIENT)
    public RestClient llmRestClient(LlmProperties properties) {
        if (properties.getApi().getMaxAttempts() < 1) {
            throw new IllegalStateException("llm.api.max-attempts must be at least 1");
        }
        return JevClientConfig.restClient(properties.getApi().getTimeout());
    }
}
