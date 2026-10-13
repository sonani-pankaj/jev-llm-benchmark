# Project Overview: Jev TypeSafe vs LLM Comparison in Spring Boot

## Executive Summary

This document outlines a Spring Boot project designed to demonstrate and compare **two fundamentally different AI model architectures** for automating enterprise decisions:

1. **Jev (TypeSafe AI)** — A "System One" model that outputs calibrated probabilities and structured decisions rather than free-form text
2. **LLMs (Large Language Models)** — Traditional generative models that output text and support function calling for structured tasks

The project's purpose is to provide a **side-by-side evaluation harness** within a Spring Boot application, showing when each model type is appropriate, how they differ in latency, cost, and reliability, and how to architect a system that uses both appropriately.

---

## The Core Distinction: Jev vs. LLM

### What Is Jev?

Jev is a transformer-based model that does **not generate text**. Instead, it produces "calibrated decisions" — probability scores for structured questions. Key characteristics:

| Attribute | Jev | Frontier LLM (GPT, Claude, Gemini) |
|---|---|---|
| **Output type** | Typed answers with probability distributions | Free-form text, tool calls |
| **Latency** | 70ms–500ms | 3–329 seconds |
| **Cost (input)** | $0.042/million tokens | $0.20–$10/million tokens |
| **Best for** | Classification, routing, scoring, structured decisions | Chat, code generation, open-ended reasoning |
| **Structured output error rate** | 0% | 0.58%–higher |

Sources: 

### What Is the LLM Role?

LLMs remain essential for tasks requiring **open-ended reasoning, text generation, or complex multi-step workflows**. The project will demonstrate LLM function calling as a mechanism to bridge natural language and structured operations.

---

## Project Structure

```
jev-vs-llm-springboot/
├── src/main/java/com/example/jevvsllm/
│   ├── JevVsLlmApplication.java
│   ├── config/
│   │   ├── JevClientConfig.java           # Jev API client setup
│   │   ├── LlmClientConfig.java           # OpenAI/Anthropic client setup
│   │   └── RoutingConfig.java             # Decision cascade configuration
│   ├── domain/
│   │   ├── SupportTicket.java             # Sample domain entity
│   │   ├── TicketCategory.java            # Enum: BILLING, BUG, ACCOUNT, SALES
│   │   ├── TicketUrgency.java             # Enum: LOW, MEDIUM, HIGH, CRITICAL
│   │   └── DecisionResult.java            # Unified output wrapper
│   ├── service/
│   │   ├── JevDecisionService.java        # Jev-based classification/routing
│   │   ├── LlmDecisionService.java        # LLM-based classification/routing
│   │   ├── HybridRoutingService.java      # Cascade: Jev first, LLM fallback
│   │   └── BenchmarkService.java          # Accuracy/latency/cost comparison
│   ├── controller/
│   │   ├── TicketController.java          # REST endpoints for ticket processing
│   │   └── BenchmarkController.java       # Run comparisons, view results
│   └── repository/
│       └── TicketRepository.java          # Persistence for audit
├── src/main/resources/
│   ├── application.yml
│   ├── prompts/
│   │   ├── ticket-classification.j2       # LLM prompt template
│   │   └── escalation-summary.j2          # LLM summarization prompt
│   └── schemas/
│       ├── ticket-routing-schema.json     # JSON Schema for structured output
│       └── urgency-scoring-schema.json
└── src/test/java/com/example/jevvsllm/
    └── BenchmarkIntegrationTest.java
```

---

## Key Components and Their Rationale

### 1. Domain Layer: Typed Decisions as First-Class Citizens

The domain model centers on **enums and typed records** rather than free text. This is deliberate: Jev requires **typed questions and schemas** to function, and LLM function calling also benefits from structured output definitions.

`TicketCategory` and `TicketUrgency` are enums that both models will classify against. This ensures apples-to-apples comparison and prevents "hallucination" in the output space — Jev literally cannot return a category outside the schema.

### 2. JevDecisionService: Fast, Cheap, Typed

**What it does:** Sends ticket text to Jev with a typed question (e.g., "What category is this ticket?") and receives a probability distribution over categories plus a confidence score.

**How it works:** Jev processes the input in a **single parallel pass**, returning all answers simultaneously. The service parses the probability distribution and selects the highest-confidence category.

**When to use this service:**
- Every incoming ticket should be routed through Jev first
- Bulk classification of historical tickets (scoring 50M rows for ~$20 in Jev calls)
- Any decision where **speed matters more than exhaustive reasoning**
- When you need **calibrated confidence** to drive escalation logic

**Spring Boot integration pattern:** The service wraps a simple HTTP client (WebClient or RestClient). No streaming, no token-by-token processing — just a request/response for a decision.

### 3. LlmDecisionService: Flexible, Powerful, Expensive

**What it does:** Uses LLM function calling (also called tool calling) to force structured output. The LLM is given a function definition like `classifyTicket(category: string, urgency: string, reasoning: string)` and must emit a JSON call matching that schema.

**How it works:** The service sends the ticket text plus the function definition. The LLM returns a function call, which the Spring Boot service parses and executes. The LLM **never runs the function** — it only returns the structured invocation.

**When to use this service:**
- When the classification requires **open-ended reasoning** or context that Jev cannot handle
- When you need **natural language explanations** accompanying the decision
- When the schema is **complex or nesting-dependent** (Jev's strength is bounded decisions, not nested extraction)
- For **low-volume, high-accuracy** scenarios where LLM's accuracy edge justifies 76x cost

**Caveat:** LLM function calling has a **0.58% structured output error rate** in some benchmarks, compared to Jev's 0%. The service must handle parse failures and retry.

### 4. HybridRoutingService: The Decision Cascade

This is the **architecturally most important component**. It implements the pattern that emerging benchmarks support:

```
Incoming Ticket
      ↓
   Jev call (fast, cheap)
      ↓
   Confidence > threshold?
      ↓ Yes                    ↓ No
   Route directly         Escalate to LLM
   (save cost/latency)    (higher accuracy)
```

**Why this pattern:** Benchmarks show Jev ties GPT-5.6 Terra on accuracy (67.8% vs 67.9%) while being **~25x faster and ~76x cheaper** per decision. But LLMs retain a 5–6 point accuracy edge on harder cases. The cascade captures Jev's efficiency for the majority of easy cases and reserves LLM spending for genuinely ambiguous inputs.

**Implementation detail:** The service exposes a `confidenceThreshold` configuration (e.g., 0.85). When Jev returns a probability distribution, if the top category's probability exceeds the threshold, the decision is final. Otherwise, the ticket is passed to `LlmDecisionService`.

### 5. BenchmarkService: Making the Comparison Visible

This service runs the **same labeled dataset** through all three paths (Jev-only, LLM-only, hybrid) and records:
- **Accuracy** (against ground truth labels)
- **Latency** (p50, p95)
- **Cost per decision** (input tokens × model rate)
- **Structured output error rate**

The `BenchmarkController` exposes endpoints like `POST /benchmark/run` and `GET /benchmark/results` so the comparison can be triggered and inspected without redeploying.

**Why this matters:** The Jev vs. LLM tradeoff is genuinely uncertain at this stage. Vendor benchmarks exist, but independent, task-specific measurement is the only reliable guide. This service turns the project into a **decision-support tool** for your specific domain.

---

## When to Use Each Model: A Decision Framework

### Use Jev When...

| Scenario | Rationale |
|---|---|
| **High-volume classification** (>10K/day) | Cost scales linearly but from a tiny base. Jev's $0.042/M input tokens vs LLM's $0.20–$10/M means 5–200x savings |
| **Latency-sensitive routing** | 70–500ms vs seconds for LLMs. For real-time ticket routing or content moderation, this is the difference between "instant" and "wait" |
| **Binary or bounded decisions** | "Does this need review?" or "Which of 5 categories?" — Jev's output space is constrained by schema |
| **Bulk scoring historical data** | Scoring 50M rows costs ~$20 with Jev vs thousands with LLM |
| **When confidence calibration matters** | Jev's probability scores are trained to be **calibrated** — a 0.9 means 90% likely correct |

### Use LLM When...

| Scenario | Rationale |
|---|---|
| **Open-ended reasoning required** | If the decision requires synthesizing multiple pieces of context or generating an explanation, LLM is appropriate |
| **Complex nested extraction** | Jev's strength is bounded decisions, not extracting nested objects from unstructured text |
| **Low volume, high accuracy** | For 100 decisions/day, the 5–6 point accuracy edge of frontier models justifies the cost |
| **Natural language explanations needed** | LLMs can produce human-readable justifications alongside the decision |
| **Schema is dynamic or deeply nested** | Function calling supports nested schemas up to 5 levels deep; Jev's typed questions are more constrained |

### The Cascade Pattern

The **hybrid approach** is not a compromise — it's the architecturally correct pattern for most production systems. Research on decision models explicitly identifies **routing as the clearest use case**: use Jev to make the fast, cheap decision about whether the LLM needs to be invoked.

The benchmark repo `jev-agent-router` shows Jev routing 111 hard support messages at **98% accuracy, 116ms median latency**, vs Claude Haiku at 99% and 607ms median — the accuracy difference is within noise, but Jev is 5x faster.

---

## Configuration Notes

### Jev Client Configuration

The Jev API expects:
- **Input:** Text/data + a typed question (or set of questions)
- **Output:** Probability distribution over possible answers

The `application.yml` should expose `jev.api.url`, `jev.api.key`, and per-request timeouts. Since Jev is a **single-pass model**, no streaming configuration is needed.

### LLM Client Configuration

The LLM client requires function definitions in JSON Schema format. Key constraints from Azure OpenAI structured outputs:
- **All fields must be required** (use `type: ["string", "null"]` for optionality)
- **`additionalProperties: false`** is mandatory
- **Up to 5 levels of nesting**, 100 total properties

The service should include **retry logic** for the ~0.5% structured output failures.

### Prompt Templates

LLM prompts should be stored as templates (Jinja2-style or Thymeleaf) in `resources/prompts/`. The `ostruct-cli` tool demonstrates the pattern of **combining a template with a JSON schema** to guarantee structured output. The same principle applies: the LLM receives both the ticket text and the schema, and must return JSON matching the schema.

---

## Success Criteria for the Project

1. **Both models produce the same typed output** (`TicketCategory`, `TicketUrgency`) so results are directly comparable
2. **The benchmark endpoint reports accuracy, p50/p95 latency, and cost per decision** for each path
3. **The hybrid cascade demonstrates measurable savings** (fewer LLM calls) without significant accuracy loss
4. **The decision framework is documented in code comments** so future developers understand when to add a new Jev question vs. an LLM prompt

---

## Conclusion

This Spring Boot project treats Jev and LLMs not as competitors but as **complementary layers in a decision architecture**. Jev handles the high-volume, low-latency, structured decisions that would be prohibitively expensive with LLMs. LLMs handle the complex reasoning and explanation generation that Jev cannot. The hybrid cascade — Jev first, LLM on low confidence — is the pattern most likely to deliver production value.

The project's lasting value is not the code itself but the **measurement harness**: by running your own labeled data through both paths, you replace vendor claims with task-specific evidence about which model belongs where in your architecture.