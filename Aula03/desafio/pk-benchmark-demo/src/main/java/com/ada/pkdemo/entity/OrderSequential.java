package com.ada.pkdemo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders_sequential")
public class OrderSequential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private BigDecimal amount;

    private final LocalDateTime createdAt = LocalDateTime.now();

    public OrderSequential() {}

    public OrderSequential(String customerName, BigDecimal amount) {
        this.customerName = customerName;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}