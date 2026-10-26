package com.ada.sagacqrs.event;

import java.math.BigDecimal;

public record OrderCreatedEvent(
        String orderId,
        String customerName,
        BigDecimal amount,
        boolean triggerSimulatedError // Flag para simular falha na SAGA e testar a DLQ
) {}