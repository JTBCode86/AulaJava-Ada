package com.melicore.engine.controller;

import com.melicore.engine.saga.OrderWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final WorkflowClient workflowClient;

    public CheckoutController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    public ResponseEntity<String> processCheckout(
            @RequestParam Double amount,
            @RequestParam(required = false, defaultValue = "CUSTOMER-DEFAULT") String customerId,
            @RequestParam(defaultValue = "PROD-123") String productId,
            @RequestParam(defaultValue = "1") int quantity) {
        
        String orderId = UUID.randomUUID().toString();

        // Configuração das opções do Workflow no Temporal
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("CHECKOUT_TASK_QUEUE")
                .setWorkflowId("order-" + orderId)
                .build();

        // Cria o stub do Workflow SAGA
        OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class, options);

        // Executa a SAGA de ponta a ponta gerida pelo Temporal
        // (Reserva de Stock -> Processamento de Pagamento com Outbox -> Emissão de Etiqueta)
        String result = workflow.processOrder(orderId, productId, quantity, amount);

        if (result.startsWith("SUCCESS")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}