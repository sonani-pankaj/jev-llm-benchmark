package com.example.jevvsllm.client;

import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword stand-in for TypeSafe Jev. Same typed output so the cascade and
 * benchmark can run with no API key. It is not the Jev model.
 */
final class LocalJevEngine {

    private static final Map<TicketCategory, String[]> CATEGORY_TERMS = Map.of(
            TicketCategory.BILLING, new String[] {
                    "invoice", "charge", "charged", "refund", "receipt", "card", "billing",
                    "payment", "checkout", "amount", "captures", "order"
            },
            TicketCategory.BUG, new String[] {
                    "crash", "crashes", "error", "500", "down", "outage", "typo", "search",
                    "dashboard", "api", "export", "bug", "defect", "slow", "page"
            },
            TicketCategory.ACCOUNT, new String[] {
                    "log in", "login", "password", "locked", "access", "profile", "email",
                    "teammate", "two-factor", "sign in", "account", "admin"
            },
            TicketCategory.SALES, new String[] {
                    "demo", "pricing", "enterprise", "upgrade", "seats", "plan", "trial",
                    "contract", "buy", "sales", "quote", "renewal", "pro"
            }
    );

    LocalJevEngine() {
    }

    JevClient.JevOutcome decide(String ticketText) {
        String text = ticketText.toLowerCase(Locale.ROOT);
        Map<TicketCategory, Integer> scores = scoreCategories(text);
        TicketCategory category = topCategory(scores);
        Map<String, Double> probabilities = probabilities(scores, ambiguous(text, scores));
        double top = probabilities.getOrDefault(category.name(), 0.0);
        TicketUrgency urgency = urgency(text);
        return new JevClient.JevOutcome(category, urgency, top, probabilities, 0, 0);
    }

    private static Map<TicketCategory, Integer> scoreCategories(String text) {
        Map<TicketCategory, Integer> scores = new EnumMap<>(TicketCategory.class);
        for (TicketCategory category : TicketCategory.values()) {
            int hits = 0;
            for (String term : CATEGORY_TERMS.get(category)) {
                if (text.contains(term)) {
                    hits++;
                }
            }
            scores.put(category, hits);
        }
        return scores;
    }

    private static TicketCategory topCategory(Map<TicketCategory, Integer> scores) {
        TicketCategory best = TicketCategory.BILLING;
        int bestScore = -1;
        for (TicketCategory category : TicketCategory.values()) {
            int score = scores.getOrDefault(category, 0);
            if (score > bestScore) {
                best = category;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean ambiguous(String text, Map<TicketCategory, Integer> scores) {
        if (text.contains("not sure") || text.contains("hard to tell") || text.contains("or maybe")
                || text.contains("ambiguous")) {
            return true;
        }
        List<Integer> ranked = new ArrayList<>(scores.values());
        ranked.sort((a, b) -> Integer.compare(b, a));
        return ranked.size() >= 2 && ranked.get(0) > 0 && ranked.get(0) - ranked.get(1) <= 1;
    }

    private static Map<String, Double> probabilities(Map<TicketCategory, Integer> scores, boolean ambiguous) {
        TicketCategory winner = topCategory(scores);
        Map<String, Double> probabilities = new LinkedHashMap<>();
        if (ambiguous) {
            probabilities.put(winner.name(), 0.40);
            double remainder = 0.60;
            TicketCategory[] others = java.util.Arrays.stream(TicketCategory.values())
                    .filter(category -> category != winner)
                    .toArray(TicketCategory[]::new);
            for (int i = 0; i < others.length; i++) {
                double share = i == others.length - 1 ? remainder : 0.20;
                remainder -= share;
                probabilities.put(others[i].name(), share);
            }
            return Map.copyOf(probabilities);
        }
        probabilities.put(winner.name(), 0.96);
        double rest = 0.04 / (TicketCategory.values().length - 1);
        for (TicketCategory category : TicketCategory.values()) {
            if (category != winner) {
                probabilities.put(category.name(), rest);
            }
        }
        return Map.copyOf(probabilities);
    }

    private static TicketUrgency urgency(String text) {
        if (containsAny(text, "right now", "down for", "launch in", "tonight", "cannot ship", "outage")) {
            return TicketUrgency.CRITICAL;
        }
        if (containsAny(text, "today", "asap", "live meeting", "this morning", "locked out", "duplicate")) {
            return TicketUrgency.HIGH;
        }
        if (containsAny(text, "whenever", "no rush", "a moment", "gathering information", "convenient")) {
            return TicketUrgency.LOW;
        }
        return TicketUrgency.MEDIUM;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
