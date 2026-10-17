package com.ada.sagacqrs.dlq;

import com.ada.sagacqrs.config.KafkaConfig;
import com.ada.sagacqrs.event.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

@Service
public class DlqConsumer {

    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_DLQ, groupId = "dlq-monitoring-group")
    public void consumeDlq(OrderCreatedEvent event,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                           @Header(KafkaHeaders.OFFSET) long offset) {
        System.err.println("🚨 [DLQ - ALERTA] Mensagem movida para a Dead Letter Queue!");
        System.err.println("   -> Tópico Original: " + topic);
        System.err.println("   -> Offset: " + offset);
        System.err.println("   -> Pedido Afetado: " + event.orderId());
        System.err.println("   -> Ação: Registrando em log de auditoria / Alerta para equipe de suporte.");
    }
}