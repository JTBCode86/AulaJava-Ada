package com.melicore.engine.scheduler;

import com.melicore.engine.config.KafkaTopicConfig;
import com.melicore.engine.domain.OutboxEvent;
import com.melicore.engine.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByProcessedFalse();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[OUTBOX POLLER] Encontrados {} eventos pendentes para publicação no Kafka.", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Tenta publicar no tópico principal de eventos
                kafkaTemplate.send(KafkaTopicConfig.ORDERS_EVENTS_TOPIC, event.getAggregateId(), event.getPayload()).get();

                event.setProcessed(true);
                outboxRepository.save(event);

                log.info("[OUTBOX POLLER] Evento {} publicado com sucesso no tópico {} (Kafka)", event.getId(), KafkaTopicConfig.ORDERS_EVENTS_TOPIC);
            } catch (Exception e) {
                log.error("[OUTBOX POLLER] Erro ao publicar evento {} no tópico principal. Redirecionando para DLQ: {}", event.getId(), e.getMessage());
                
                try {
                    // Fallback: Publica na Dead Letter Queue (orders.dlq)
                    kafkaTemplate.send(KafkaTopicConfig.ORDERS_DLQ_TOPIC, event.getAggregateId(), event.getPayload()).get();
                    
                    event.setProcessed(true); // Marca como processado via DLQ para não reter o poller
                    outboxRepository.save(event);
                    
                    log.warn("[OUTBOX POLLER] Evento {} gravado na DLQ ({}) com sucesso.", event.getId(), KafkaTopicConfig.ORDERS_DLQ_TOPIC);
                } catch (Exception dlqException) {
                    log.error("[OUTBOX POLLER] CRÍTICO: Falha ao gravar evento {} na DLQ: {}", event.getId(), dlqException.getMessage());
                }
            }
        }
    }
}