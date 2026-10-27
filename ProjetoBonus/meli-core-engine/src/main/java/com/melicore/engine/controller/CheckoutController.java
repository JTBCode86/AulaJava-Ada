package com.melicore.engine.controller;

import com.melicore.engine.saga.OrderWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
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

    // Record para mapear corretamente o JSON enviado pelo k6
    public record CheckoutRequest(
            Double amount,
            String customerId,
            String productId,
            Integer quantity
    ) {}

    @PostMapping
    public ResponseEntity<String> processCheckout(@RequestBody CheckoutRequest request) {
        
        String orderId = UUID.randomUUID().toString();

        // Configuração das opções do Workflow no Temporal
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("CHECKOUT_TASK_QUEUE")
                .setWorkflowId("order-" + orderId)
                .build();

        // Cria o stub do Workflow SAGA
        OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class, options);

        // DISPARA O WORKFLOW DE FORMA ASSÍNCRONA (Não bloqueia a thread HTTP!)
        WorkflowExecution execution = WorkflowClient.start(
                workflow::processOrder,
                orderId,
                request.productId() != null ? request.productId() : "PROD-MELI-01",
                request.quantity() != null ? request.quantity() : 1,
                request.amount() != null ? request.amount() : 150.00
        );

        // Retorna imediatamente 202 Accepted com o ID do workflow gerado
        return ResponseEntity.accepted().body("SUCCESS: Workflow started with ID " + execution.getWorkflowId());
    }
}