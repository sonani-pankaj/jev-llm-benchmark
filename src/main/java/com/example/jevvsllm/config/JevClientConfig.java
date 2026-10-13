package com.example.jevvsllm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Jev is a single-pass decision API: one POST, no streaming.
 * Add a question here when the judgment is bounded — a fixed label set (choice),
 * an ordered scale (score), or a yes/no gate (noul). Put open-ended explanation
 * in an LLM prompt instead.
 */
@Configuration
public class JevClientConfig {

    public static final String REST_CLIENT = "jevRestClient";

    @Bean(name = REST_CLIENT)
    public RestClient jevRestClient(JevProperties properties) {
        return restClient(properties.getApi().getTimeout());
    }

    static RestClient restClient(java.time.Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
