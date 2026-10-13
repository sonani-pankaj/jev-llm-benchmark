package com.example.jevvsllm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private final Api api = new Api();
    private final Pricing pricing = new Pricing();

    public Api getApi() {
        return api;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public static class Api {
        private String url = "http://127.0.0.1:11434/v1/chat/completions";
        private String key = "";
        private String model = "qwen2.5-coder:14b-instruct-q4_K_M";
        private Duration timeout = Duration.ofSeconds(45);
        private int maxAttempts = 1;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class Pricing {
        private BigDecimal inputPerMillionUsd = BigDecimal.ZERO;
        private BigDecimal outputPerMillionUsd = BigDecimal.ZERO;

        public BigDecimal getInputPerMillionUsd() {
            return inputPerMillionUsd;
        }

        public void setInputPerMillionUsd(BigDecimal inputPerMillionUsd) {
            this.inputPerMillionUsd = inputPerMillionUsd;
        }

        public BigDecimal getOutputPerMillionUsd() {
            return outputPerMillionUsd;
        }

        public void setOutputPerMillionUsd(BigDecimal outputPerMillionUsd) {
            this.outputPerMillionUsd = outputPerMillionUsd;
        }
    }
}
