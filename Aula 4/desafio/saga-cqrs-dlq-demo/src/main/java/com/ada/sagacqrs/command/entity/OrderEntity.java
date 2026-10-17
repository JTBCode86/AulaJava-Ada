package com.ada.sagacqrs.command.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orders_command")
public class OrderEntity {
    @Id
    private String id;
    private String customerName;
    private BigDecimal amount;
    private String status;

    public OrderEntity() {}
    public OrderEntity(String id, String customerName, BigDecimal amount, String status) {
        this.id = id;
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
    }

    public String getId() { return id; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}