package com.melicore.engine.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public String processPayment(String orderId, Double amount) {
        // Simulando falha ou instabilidade no gateway externo
        if (amount != null && amount > 10000.0) {
            throw new RuntimeException("Gateway de Pagamento Indisponível (Timeout 504)");
        }
        return "SUCCESS - Pagamento aprovado para o pedido " + orderId;
    }

    // Método de Fallback executado quando o Circuit Breaker abre
    public String paymentFallback(String orderId, Double amount, Throwable t) {
        // Degradação graciosa: Salva o pedido como pendente de retentativa assíncrona
        return "FALLBACK_QUEUED - Pagamento diferido para o pedido " + orderId + ". Motivo: " + t.getMessage();
    }
}