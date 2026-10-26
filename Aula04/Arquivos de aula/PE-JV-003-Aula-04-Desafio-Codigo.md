# Prática: Laboratórios Oficiais e Padrões de Mercado (Trilha Prática)

> **Sobre este guia:**
>
> Este documento reúne laboratórios e referências oficiais da indústria (Temporal.io, Debezium e Confluent) que demonstram na prática as soluções para os desafios de sistemas distribuídos vistos na aula.
>
> Em vez de construir ambientes distribuídos caseiros do zero, você encontra aqui projetos prontos e mantidos pelos criadores das próprias tecnologias, com instruções diretas de execução e roteiro de observação de comportamento.

---

## 🗺️ Matriz: O Incidente da Black Friday vs. Laboratórios Oficiais

| Desafio Arquitetural do Marketplace | Referência Oficial Recomendada | O que você executa / inspeciona |
| :--- | :--- | :--- |
| **Orquestrar estorno reverso se o estoque falhar** | [Temporal Booking SAGA](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/bookingsaga) | Executa o workflow e vê o cancelamento automático na Web UI. |
| **Garantir consistência financeira sem 2PC** | [Temporal Money Transfer](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/moneytransfer) | Simula saque, depósito e estorno automático em falhas. |
| **Eliminar o Dual Write no Checkout** | [Debezium Outbox Pattern](https://github.com/debezium/debezium-examples/tree/main/outbox) | Sobe Docker Compose e vê o Postgres gravando pedido + outbox via CDC. |
| **Consumo assíncrono com Dead Letter Queue** | [Confluent Spring Boot & Kafka](https://developer.confluent.io/get-started/spring-boot/) | Tutorial passo a passo de listener e descarte de eventos venenosos. |
| **Desacoplar consultas sem onerar o banco de escrita** | [Confluent CQRS](https://developer.confluent.io/courses/event-sourcing/cqrs/) & [Dual Write](https://developer.confluent.io/courses/microservices/the-dual-write-problem/) | Diagramas de fluxo para projeção assíncrona de leituras. |

---

## 1. Trilha: Orquestração Durável com Temporal.io (Java SDK)

O repositório oficial [temporalio/samples-java](https://github.com/temporalio/samples-java) fornece projetos Java prontos para rodar localmente. O principal objetivo prático é executar a aplicação e acompanhar a árvore de execução e as compensações diretamente na interface gráfica nativa do Temporal.

### Exemplo 1: `Booking SAGA`

* **Código-Fonte:** [io/temporal/samples/bookingsaga](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/bookingsaga)
* **O Conceito:** Orquestra um fluxo distribuído de três etapas (*Carro $\rightarrow$ Voo $\rightarrow$ Hotel*). O código força propositalmente uma falha no Hotel para acionar a mecânica de compensação automática.
* **Conexão com a Aula:** Representa o fluxo de checkout (*Criar Pedido $\rightarrow$ Debitar Cartão $\rightarrow$ Reservar Estoque*). Quando a reserva de estoque falha por esgotamento de SKU na Black Friday, a classe utilitária `io.temporal.workflow.Saga` desfaz as etapas anteriores em ordem inversa (*Estornar Cartão $\rightarrow$ Cancelar Pedido*).
* **Como testar:**
  1. Inicie o servidor local do Temporal (sem necessidade de Docker):

     ```bash
     temporal server start-dev
     ```

  2. Execute a classe `TripBookingWorkflow` via Gradle ou Maven:

     ```bash
     ./gradlew run -DmainClass=io.temporal.samples.bookingsaga.TripBookingWorkflow
     ```

  3. Acesse a Web UI em [http://localhost:8233](http://localhost:8233) e observe:
     * As atividades concluídas com sucesso em verde.
     * A falha do hotel em vermelho.
     * O disparo das atividades de compensação (`cancelFlight`, `cancelCar`) em ordem estritamente reversa.

### Exemplo 2: `Money Transfer`

* **Código-Fonte:** [io/temporal/samples/moneytransfer](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/moneytransfer)
* **O Conceito:** Simula uma transação financeira entre duas contas bancárias (*Saque na Conta A $\rightarrow$ Depósito na Conta B*).
* **Conexão com a Aula:** Ilustra a gestão de estados intermediários (*PENDING*, *FAILED*, *REFUNDED*) e a garantia de que o saldo do cliente permaneça íntegro mesmo sob quedas parciais de rede ou indisponibilidade de serviços bancários externos.

---

## 2. Trilha: Transactional Outbox Pattern com Debezium

Ambiente oficial via Docker Compose mantido pelos criadores do Debezium (Red Hat). O ambiente é pré-configurado: você executa comandos HTTP e inspeciona a sincronização em tempo real entre PostgreSQL e Apache Kafka via CDC (*Change Data Capture*).

* **Código-Fonte e Laboratório:** [debezium-examples/outbox](https://github.com/debezium/debezium-examples/tree/main/outbox)
* **Documentação da SMT Outbox:** [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
* **O Conceito:** Eliminação do problema do *Dual Write*. Em vez de salvar no banco relacional e depois tentar publicar no broker, o serviço grava o pedido e o evento na tabela `outboxevent` dentro da mesma transação ACID local. O Debezium monitora o Write-Ahead Log (WAL) do Postgres e publica a mensagem no Kafka de forma assíncrona.
* **O que observar na prática:**
  1. Inicialize a stack de containers:

     ```bash
     docker compose up --build
     ```

  2. Dispare a criação de um pedido:

     ```bash
     curl -X POST http://localhost:8080/orders \
       -H "Content-Type: application/json" \
       -d '{"customerId": 1, "orderValue": 99.90}'
     ```

  3. **No PostgreSQL:** Inspecione e comprove que o registro de pedido e a linha em `outboxevent` foram gravados na mesma transação relacional.
  4. **No Apache Kafka:** Verifique que o conector CDC identificou a gravação no WAL e despachou o evento estruturado para o tópico, sem que o código do serviço de pedidos tenha chamado bibliotecas do Kafka diretamente.
  5. **No Serviço de Entrega (`shipment-service`):** Observe o consumo assíncrono do evento para iniciar a separação logística.

---

## 3. Trilha: Coreografia, Resiliência e CQRS com Confluent

Seleção de referências práticas e conceituais do portal oficial [Confluent Developer](https://developer.confluent.io/).

### Tutorial Prático: Spring Boot + Apache Kafka (com DLQ)

* **Link Oficial:** [Confluent: Getting Started with Spring Boot](https://developer.confluent.io/get-started/spring-boot/)
* **O Conceito:** Demonstra o consumo assíncrono com `@KafkaListener` e o roteamento de falhas para *Dead Letter Queues* (DLQ).
* **Conexão com a Aula:** Aplicação essencial para arquiteturas baseadas em **Coreografia**. Demonstra como isolar mensagens malformadas ou com falhas de negócio sem travar o processamento da partição ou degradar a vazão geral do marketplace.

### Guias de Arquitetura Essenciais

* **O Problema do Dual Write:** [The Dual-Write Problem (Confluent)](https://developer.confluent.io/courses/microservices/the-dual-write-problem/)
  * *Por que consultar:* Explicação visual e fundamentada sobre por que gravar no banco de dados e publicar no broker de forma sequencial resulta em inconsistências graves e dados órfãos sob alta concorrência.
* **Padrão CQRS:** [CQRS Pattern (Confluent)](https://developer.confluent.io/courses/event-sourcing/cqrs/)
  * *Por que consultar:* Estruturação de visões materializadas assíncronas para atender painéis de consulta de pedidos e relatórios de clientes sem concorrer por CPU e disco com as transações de escrita do checkout.

---

## 🎯 Conexão com seu Documento de System Design

Utilize as evidências e dinâmicas desses laboratórios para fundamentar as decisões registradas no seu projeto:

1. **Coordenação da Saga (Seção 1):** Como seu desenho aborda os trade-offs entre Coreografia e Orquestração? Se optou por Orquestração, como mitigou o acoplamento do orquestrador (conforme o modelo do Temporal)? Se optou por Coreografia, como garantiu observabilidade ponta a ponta e isolamento via DLQ (como visto na Confluent)?
2. **Compensação e Outbox (Seção 2):** Como a adoção do Transactional Outbox (modelo Debezium) garante a entrega de eventos aos consumidores sem recorrer ao bloqueio de recursos do 2PC e sem expor o checkout ao risco de Dual Write?
3. **Consistência Eventual e CQRS (Seção 3):** De que forma a separação entre comandos de escrita e réplicas de leitura otimizadas preserva a estabilidade do marketplace durante picos de acesso?
