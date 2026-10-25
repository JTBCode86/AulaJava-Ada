package com.melicore.engine.saga;

import com.melicore.engine.domain.OutboxEvent;
import com.melicore.engine.repository.OutboxRepository;
import com.melicore.engine.service.PaymentGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

    private final PaymentGatewayService paymentGatewayService;
    private final OutboxRepository outboxRepository;

    public OrderActivitiesImpl(PaymentGatewayService paymentGatewayService, OutboxRepository outboxRepository) {
        this.paymentGatewayService = paymentGatewayService;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public boolean reserveStock(String orderId, String productId, int quantity) {
        log.info("[SAGA] Reservando {} unidades do produto {} para o pedido {}", quantity, productId, orderId);
        return true; 
    }

    @Override
    public void releaseStock(String orderId, String productId, int quantity) {
        log.info("[SAGA COMPENSAÇÃO] Libertando estoque do produto {} para o pedido {}", productId, orderId);
    }

    @Override
    @Transactional
    public boolean processPayment(String orderId, Double amount) {
        log.info("[SAGA] Processando pagamento de {} para o pedido {}", amount, orderId);
        
        try {
            // Ajustado para receber os parâmetros corretos conforme a assinatura do seu serviço
            String paymentResult = paymentGatewayService.processPayment(orderId, amount);
            boolean approved = paymentResult != null && paymentResult.startsWith("SUCCESS");
            
            if (approved) {
                OutboxEvent outboxEvent = new OutboxEvent();
                outboxEvent.setId(UUID.randomUUID());
                outboxEvent.setAggregateType("ORDER");
                outboxEvent.setAggregateId(orderId);
                outboxEvent.setEventType("PAYMENT_APPROVED");
                outboxEvent.setPayload("{\"amount\": " + amount + ", \"status\": \"APPROVED\", \"orderId\": \"" + orderId + "\"}");
                outboxEvent.setCreatedAt(LocalDateTime.now());
                
                outboxRepository.save(outboxEvent);
            }
            
            return approved;
        } catch (Exception e) {
            log.error("[SAGA] Erro ao processar pagamento: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void refundPayment(String orderId, Double amount) {
        log.info("[SAGA COMPENSAÇÃO] Efetuando estorno do pagamento de {} para o pedido {}", amount, orderId);
    }

    @Override
    public String generateShippingLabel(String orderId) {
        log.info("[SAGA] Gerando etiqueta de envio para o pedido {}", orderId);
        return "MELI-LABEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}