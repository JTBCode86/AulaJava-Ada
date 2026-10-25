package com.melicore.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"orders.events", "orders.dlq"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"}
)
class CheckoutEngineApplicationTests {

    @Test
    @DisplayName("Deve carregar o contexto da aplicação e infraestrutura de teste com sucesso")
    void contextLoads() {
        // Validação da inicialização de Beans, Spring Data JPA, Flyway e Kafka embarcado
    }
}