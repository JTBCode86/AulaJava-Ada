package com.ada.sagacqrs.event;

import java.math.BigDecimal;

public record OrderPaidEvent(
        String orderId,
        String paymentId,
        BigDecimal amount,
        String status
) {}