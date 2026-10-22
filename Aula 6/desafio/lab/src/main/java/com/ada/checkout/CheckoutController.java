package com.ada.checkout;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    @PostMapping
    public ResponseEntity<Map<String, Object>> processCheckout(@RequestBody Map<String, Object> payload) {
        String orderId = UUID.randomUUID().toString();
        log.info("Iniciando processamento do pedido order_id={}", orderId);

        // Simulando processamento ou gargalo downstream
        try {
            Thread.sleep(50); // latência normal simulada
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return ResponseEntity.ok(Map.of(
                "order_id", orderId,
                "status", "SUCCESS",
                "message", "Checkout realizado com sucesso"
        ));
    }
}