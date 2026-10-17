package com.ada.checkout.resiliencedemo.controller;

import com.ada.checkout.resiliencedemo.service.ExternalPaymentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final ExternalPaymentService paymentService;

    public CheckoutController(ExternalPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public ResponseEntity<String> realizarCheckout(@RequestParam Double valor) {
        String resposta = paymentService.processPayment(valor);
        return ResponseEntity.ok(resposta);
    }

    // Método de Fallback executado quando o circuito abre ou o serviço externo falha
    public ResponseEntity<String> paymentFallback(Double valor, Throwable t) {
        return ResponseEntity.status(503).body(
                "⚠️ [FALLBACK ATIVADO] O serviço de pagamento está indisponível no momento. " +
                        "Seu pedido de R$ " + valor + " foi salvo para processamento posterior. " +
                        "Motivo: " + t.getMessage()
        );
    }
}