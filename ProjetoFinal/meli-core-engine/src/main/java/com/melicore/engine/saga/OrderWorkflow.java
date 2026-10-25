package com.melicore.engine.saga;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderWorkflow {

    @WorkflowMethod
    String processOrder(String orderId, String productId, int quantity, Double amount);
}