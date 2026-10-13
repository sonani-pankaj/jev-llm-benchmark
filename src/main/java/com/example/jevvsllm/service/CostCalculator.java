package com.example.jevvsllm.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CostCalculator {

    public BigDecimal estimate(long inputTokens, long outputTokens, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
        BigDecimal input = inputPerMillion.multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal output = outputPerMillion.multiply(BigDecimal.valueOf(outputTokens));
        return input.add(output).divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
    }
}
