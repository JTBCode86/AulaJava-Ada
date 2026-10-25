package com.melicore.engine.service;

import com.melicore.engine.domain.OutboxEvent;
import com.melicore.engine.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxProcessorService {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC_NAME = "payment-events";

    public OutboxProcessorService(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000) // Varredura executada a cada 5 segundos
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxRepository.findAll();

        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            try {
                // Publica no Kafka utilizando o aggregateId como chave para preservar a ordenação por pedido
                kafkaTemplate.send(TOPIC_NAME, event.getAggregateId(), event.getPayload()).get();

                System.out.printf("[OUTBOX SERVICE] Evento publicado no Kafka com sucesso! ID: %s | Tipo: %s%n", 
                        event.getId(), event.getEventType());

            } catch (Exception e) {
                System.err.printf("[OUTBOX SERVICE ERROR] Falha ao publicar evento ID: %s. Erro: %s%n", 
                        event.getId(), e.getMessage());
                // Lança exceção para provocar o rollback e tentar novamente no próximo ciclo
                throw new RuntimeException("Falha na publicação Kafka, revertendo transação.", e);
            }
        }

        // Remove os eventos publicados da tabela de outbox
        outboxRepository.deleteAll(events);
    }
}