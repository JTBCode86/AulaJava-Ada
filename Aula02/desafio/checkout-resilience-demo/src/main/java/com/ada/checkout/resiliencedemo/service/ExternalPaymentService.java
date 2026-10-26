package com.ada.checkout.resiliencedemo.service;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class ExternalPaymentService {

    private final Random random = new Random();

    public String processPayment(Double amount) {
        // Simulando falha aleatória ou queda total do serviço de pagamento
        boolean simulateFailure = true; // Altere para true para forçar a queda

        if (simulateFailure) {
            throw new RuntimeException("Gateway de Pagamento Indisponível (Erro 500)!");
        }

        return "Pagamento de R$ " + amount + " processado com sucesso pelo parceiro!";
    }
}