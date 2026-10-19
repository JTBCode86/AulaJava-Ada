package com.adatest.checkout.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", nullable = false)
    private StatusPagamento statusPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_saga")
    private StatusPagamento estadoSaga;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    public Pagamento() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Pagamento(BigDecimal valor, StatusPagamento status) {
        this.valor = valor;
        this.statusPagamento = status;
        this.estadoSaga = status; // Dual Write para retrocompatibilidade
        this.dataCriacao = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void syncFields() {
        if (this.estadoSaga == null && this.statusPagamento != null) {
            this.estadoSaga = this.statusPagamento;
        } else if (this.statusPagamento == null && this.estadoSaga != null) {
            this.statusPagamento = this.estadoSaga;
        }
    }

    public UUID getId() { return id; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public StatusPagamento getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(StatusPagamento statusPagamento) { this.statusPagamento = statusPagamento; }
    public StatusPagamento getEstadoSaga() { return estadoSaga; }
    public void setEstadoSaga(StatusPagamento estadoSaga) { this.estadoSaga = estadoSaga; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}