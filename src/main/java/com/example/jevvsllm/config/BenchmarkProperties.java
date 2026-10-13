package com.example.jevvsllm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkProperties {

    private String dataset = "classpath:dataset/labeled-tickets.json";
    /** Cap used when POST omits limit. 0 means the full file. */
    private int defaultLimit = 4;

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }
}
