package com.ada.pkdemo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders_uuid")
public class OrderUuid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String customerName;
    private BigDecimal amount;
    private LocalDateTime createdAt = LocalDateTime.now();

    public OrderUuid() {}

    public OrderUuid(String customerName, BigDecimal amount) {
        this.customerName = customerName;
        this.amount = amount;
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}