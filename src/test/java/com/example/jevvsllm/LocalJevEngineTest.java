package com.example.jevvsllm;

import com.example.jevvsllm.client.JevClient;
import com.example.jevvsllm.domain.TicketCategory;
import com.example.jevvsllm.domain.TicketUrgency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "jev.api.mode=local",
        "spring.datasource.url=jdbc:h2:mem:localjev;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LocalJevEngineTest {

    @Autowired
    JevClient jevClient;

    @Test
    void localModeClassifiesWithoutRemoteKey() {
        JevClient.JevOutcome billing = jevClient.decide(
                "I was charged twice for the April invoice and need the duplicate refunded today."
        );
        assertThat(billing.category()).isEqualTo(TicketCategory.BILLING);
        assertThat(billing.urgency()).isEqualTo(TicketUrgency.HIGH);
        assertThat(billing.topCategoryProbability()).isGreaterThan(0.85);

        JevClient.JevOutcome mixed = jevClient.decide(
                "The invoice looks wrong, but the billing page also crashes. I am not sure which team should look."
        );
        assertThat(mixed.topCategoryProbability()).isLessThan(0.85);
        assertThat(TicketCategory.valueOf(mixed.category().name())).isNotNull();
    }
}
