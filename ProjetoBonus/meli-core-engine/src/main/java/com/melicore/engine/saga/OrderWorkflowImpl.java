package com.melicore.engine.saga;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class OrderWorkflowImpl implements OrderWorkflow {

    // Configuração das opções de Timeout e Retry para as atividades
    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .build())
                    .build()
    );

    @Override
    public String processOrder(String orderId, String productId, int quantity, Double amount) {
        boolean stockReserved = false;
        boolean paymentProcessed = false;

        try {
            // Passo 1: Reservar Estoque no Marketplace
            stockReserved = activities.reserveStock(orderId, productId, quantity);
            if (!stockReserved) {
                return "FAILED - Estoque insuficiente para o produto " + productId;
            }

            // Passo 2: Processar Pagamento (Gateway)
            paymentProcessed = activities.processPayment(orderId, amount);
            if (!paymentProcessed) {
                // Compensação SAGA: Se o pagamento falhar, libera o estoque reservado
                activities.releaseStock(orderId, productId, quantity);
                return "FAILED - Pagamento recusado. Estoque estornado com sucesso (SAGA Compensada).";
            }

            // Passo 3: Gerar Etiqueta de Envio (Logística Meli)
            String shippingLabel = accoladeShippingLabel(orderId); // Método auxiliar ou chamada direta à atividade

            return "SUCCESS - Pedido processado e etiqueta gerada: " + shippingLabel;

        } catch (Exception e) {
            // Em caso de falha sistêmica ou exceção não tratada, executa compensações reversas
            if (paymentProcessed) {
                activities.refundPayment(orderId, amount);
            }
            if (stockReserved) {
                activities.releaseStock(orderId, productId, quantity);
            }
			throw Workflow.wrap(e);
        }
    }

    private String accoladeShippingLabel(String orderId) {
        return activities.generateShippingLabel(orderId);
    }
}