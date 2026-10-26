package com.adatest.checkout.controller;

import com.adatest.checkout.domain.Pagamento;
import com.adatest.checkout.domain.StatusPagamento;
import com.adatest.checkout.repository.PagamentoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {

    private final PagamentoRepository repository;

    public PagamentoController(PagamentoRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Pagamento> criar(@RequestParam BigDecimal valor) {
        Pagamento novo = new Pagamento(valor, StatusPagamento.PENDENTE);
        return ResponseEntity.ok(repository.save(novo));
    }

    @GetMapping
    public ResponseEntity<List<Pagamento>> listarTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> healthcheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "checkout-service"));
    }
}