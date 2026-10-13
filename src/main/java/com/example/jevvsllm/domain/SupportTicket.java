package com.example.jevvsllm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketUrgency urgency;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionPath path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelSource resolvedBy;

    private long latencyMs;

    @Column(precision = 18, scale = 8)
    private BigDecimal estimatedCost;

    @Column(length = 4000)
    private String reasoning;

    @Column(length = 2000)
    private String categoryProbabilitiesJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected SupportTicket() {
    }

    public static SupportTicket from(String text, DecisionResult decision, String probabilitiesJson) {
        SupportTicket ticket = new SupportTicket();
        ticket.text = text;
        ticket.category = decision.category();
        ticket.urgency = decision.urgency();
        ticket.confidence = decision.confidence();
        ticket.path = decision.path();
        ticket.resolvedBy = decision.resolvedBy();
        ticket.latencyMs = decision.latencyMs();
        ticket.estimatedCost = decision.estimatedCost();
        ticket.reasoning = decision.reasoning();
        ticket.categoryProbabilitiesJson = probabilitiesJson;
        return ticket;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public TicketUrgency getUrgency() {
        return urgency;
    }

    public Double getConfidence() {
        return confidence;
    }

    public DecisionPath getPath() {
        return path;
    }

    public ModelSource getResolvedBy() {
        return resolvedBy;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getCategoryProbabilitiesJson() {
        return categoryProbabilitiesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StoredTicket toStoredTicket() {
        return new StoredTicket(
                id,
                text,
                category,
                urgency,
                confidence,
                path,
                resolvedBy,
                latencyMs,
                estimatedCost,
                reasoning,
                createdAt
        );
    }
}
