package com.ada.sagacqrs.saga;

import com.ada.sagacqrs.config.KafkaConfig;
import com.ada.sagacqrs.event.OrderCreatedEvent;
import com.ada.sagacqrs.event.OrderPaidEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentSagaConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentSagaConsumer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC, groupId = "payment-saga-group")
    public void processPayment(OrderCreatedEvent event) {
        System.out.println("💳 [SAGA - Pagamento] Processando pedido: " + event.orderId());

        if (event.triggerSimulatedError()) {
            System.err.println("❌ [SAGA - Pagamento] FALHA SIMULADA! Lançando exceção...");
            throw new RuntimeException("Simulação de indisponibilidade no Gateway de Pagamento!");
        }

        // Sucesso do Pagamento na SAGA
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        OrderPaidEvent paidEvent = new OrderPaidEvent(event.orderId(), paymentId, event.amount(), "PAID");

        System.out.println("✅ [SAGA - Pagamento] Pagamento aprovado (" + paymentId + "). Notificando CQRS...");
        kafkaTemplate.send(KafkaConfig.ORDER_PAID_TOPIC, event.orderId(), paidEvent);
    }
}