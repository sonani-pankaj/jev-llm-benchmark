package com.example.jevvsllm.service;

import com.example.jevvsllm.client.ClasspathText;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptCatalog {

    private final String classificationTemplate;
    private final String escalationTemplate;

    public PromptCatalog() {
        this.classificationTemplate = ClasspathText.read("prompts/ticket-classification.j2");
        this.escalationTemplate = ClasspathText.read("prompts/escalation-summary.j2");
    }

    public String classification(String ticketText) {
        return render(classificationTemplate, Map.of("ticket_text", ticketText));
    }

    public String escalation(String ticketText, TicketCategory category, TicketUrgency urgency) {
        return render(escalationTemplate, Map.of(
                "ticket_text", ticketText,
                "category", category.name(),
                "urgency", urgency.name()
        ));
    }

    private static String render(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }
}
