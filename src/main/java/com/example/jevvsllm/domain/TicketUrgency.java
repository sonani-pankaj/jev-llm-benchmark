package com.example.jevvsllm.domain;

/**
 * Ordered urgency scale. Ordinal 0 is LOW and ordinal 3 is CRITICAL, matching
 * the Jev score levels in urgency-scoring-schema.json.
 */
public enum TicketUrgency {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static TicketUrgency fromScore(double score) {
        int level = (int) Math.round(score);
        if (level < 0) {
            level = 0;
        }
        TicketUrgency[] values = values();
        if (level >= values.length) {
            level = values.length - 1;
        }
        return values[level];
    }
}
