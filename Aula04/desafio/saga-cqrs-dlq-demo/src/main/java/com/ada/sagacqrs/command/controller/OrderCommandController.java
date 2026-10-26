package com.ada.sagacqrs.command.controller;

import com.ada.sagacqrs.command.entity.OrderEntity;
import com.ada.sagacqrs.command.repository.OrderRepository;
import com.ada.sagacqrs.config.KafkaConfig;
import com.ada.sagacqrs.event.OrderCreatedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/commands/orders")
public class OrderCommandController {

    private final OrderRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderCommandController(OrderRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<String> createOrder(@RequestParam String customerName,
                                              @RequestParam BigDecimal amount,
                                              @RequestParam(defaultValue = "false") boolean simulateError) {
        String orderId = UUID.randomUUID().toString();
        OrderEntity order = new OrderEntity(orderId, customerName, amount, "PENDING");
        repository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerName, amount, simulateError);
        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, orderId, event);

        return ResponseEntity.ok("Pedido criado com ID: " + orderId + " (Status: PENDING)");
    }
}