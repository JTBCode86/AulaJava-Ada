package com.ada.sagacqrs.query.controller;

import com.ada.sagacqrs.config.KafkaConfig;
import com.ada.sagacqrs.event.OrderPaidEvent;
import com.ada.sagacqrs.query.model.OrderReadModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/queries/orders")
public class OrderQueryController {

    // Simulação de Read Model / Vista Materializada na memória
    private final Map<String, OrderReadModel> readStore = new ConcurrentHashMap<>();

    @KafkaListener(topics = KafkaConfig.ORDER_PAID_TOPIC, groupId = "cqrs-query-group")
    public void projectOrderPaid(OrderPaidEvent event) {
        System.out.println("📊 [CQRS - Query Model] Atualizando projeção de leitura para o pedido: " + event.orderId());
        readStore.put(event.orderId(), new OrderReadModel(
                event.orderId(),
                "Cliente SAGA",
                event.amount(),
                "COMPLETED",
                event.paymentId()
        ));
    }

    @GetMapping
    public Collection<OrderReadModel> getAllOrders() {
        return readStore.values();
    }

    @GetMapping("/{orderId}")
    public OrderReadModel getOrderById(@PathVariable String orderId) {
        return readStore.get(orderId);
    }
}