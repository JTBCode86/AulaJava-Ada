package com.ada.sagacqrs.query.model;

import java.math.BigDecimal;

public record OrderReadModel(
        String orderId,
        String customerName,
        BigDecimal amount,
        String status,
        String paymentId
) {}