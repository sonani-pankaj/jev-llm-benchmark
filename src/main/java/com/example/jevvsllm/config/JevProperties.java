package com.example.jevvsllm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "jev")
public class JevProperties {

    private final Api api = new Api();
    private final Pricing pricing = new Pricing();

    public Api getApi() {
        return api;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public static class Api {
        /**
         * {@code local} is a free keyword stand-in. {@code remote} calls TypeSafe
         * or another System One host and needs {@code TYPESAFE_API_KEY}.
         */
        private String mode = "local";
        private String url = "https://api.typesafe.ai/v1/systemone";
        private String key = "";
        private String model = "jev-latest";
        private Duration timeout = Duration.ofSeconds(5);

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

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
    }

    public static class Pricing {
        private BigDecimal inputPerMillionUsd = new BigDecimal("0.042");
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
