package com.example.jevvsllm;

import com.example.jevvsllm.config.BenchmarkProperties;
import com.example.jevvsllm.config.JevProperties;
import com.example.jevvsllm.config.LlmProperties;
import com.example.jevvsllm.config.RoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JevProperties.class,
        LlmProperties.class,
        RoutingProperties.class,
        BenchmarkProperties.class
})
public class JevVsLlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(JevVsLlmApplication.class, args);
    }
}
