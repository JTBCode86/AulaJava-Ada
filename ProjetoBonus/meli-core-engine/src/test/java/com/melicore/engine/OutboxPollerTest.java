package com.melicore.engine;
import com.melicore.engine.scheduler.OutboxPoller;
import com.melicore.engine.domain.OutboxEvent;
import com.melicore.engine.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"orders.events", "orders.dlq"})
class OutboxPollerTest {

    @Autowired
    private OutboxPoller outboxPoller;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @Transactional
    @DisplayName("Deve processar evento pendente da Outbox e marcar como processado após envio ao Kafka")
    void shouldPollAndProcessOutboxEventSuccessfully() {
        // 1. Dado um evento salvo na tabela outbox_events como não processado (processed = false)
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("ORDER");
        event.setAggregateId("ORDER-TEST-123");
        event.setEventType("PAYMENT_APPROVED");
        event.setPayload("{\"orderId\":\"ORDER-TEST-123\", \"status\":\"APPROVED\"}");
        event.setCreatedAt(LocalDateTime.now());
        event.setProcessed(false);

        outboxRepository.save(event);

        // 2. Quando o poller é executado
        outboxPoller.pollOutboxEvents();

        // 3. Então o evento deve ter o seu status atualizado para processado (processed = true)
        OutboxEvent updatedEvent = outboxRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.isProcessed()).isTrue();
    }
}