package com.melicore.engine.saga;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    boolean reserveStock(String orderId, String productId, int quantity);

    @ActivityMethod
    void releaseStock(String orderId, String productId, int quantity); // Ação compensatória (SAGA)

    @ActivityMethod
    boolean processPayment(String orderId, Double amount);

    @ActivityMethod
    void refundPayment(String orderId, Double amount); // Ação compensatória (SAGA)

    @ActivityMethod
    String generateShippingLabel(String orderId);
}