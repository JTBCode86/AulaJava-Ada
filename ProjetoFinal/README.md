# MeliCore Checkout Engine

Um sistema de checkout de microsserviços de alto desempenho, resiliente e orientado a eventos, construído com Spring Boot, Temporal.io, PostgreSQL e Apache Kafka. O projeto implementa orquestração de transações distribuídas usando o **Padrão SAGA** e garante a entrega confiável de eventos via **Padrão Transactional Outbox**.

---

## 📌 Sumário
- [Descrição](#-descrição)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Arquitetura](#-arquitetura)
- [Estrutura de Pastas](#-estrutura-de-pastas)
- [Guia de Execução Passo a Passo](#-guia-de-execução-passo-a-passo)
- [Visualização de Ordens Processadas (Portal Temporal)](#-visualização-de-ordens-processadas-portal-temporal)
- [Trade-offs de Arquitetura](#-trade-offs-de-arquitetura)
- [Resolução de Problemas (Troubleshooting)](#-resolução-de-problemas-troubleshooting)

---

## 📖 Descrição

O **MeliCore Checkout Engine** processa fluxos de checkout de e-commerce sob garantias de alta resiliência e consistência transacional. 

Ele resolve o problema de escrita dupla (*dual-write problem*) entre fronteiras de microsserviços combinando:
1. **Temporal.io** para orquestração de workflows SAGA baseada em estado (Reserva de Estoque $\rightarrow$ Processamento de Pagamento $\rightarrow$ Geração da Etiqueta de Envio) com ações de compensação automáticas em caso de falhas.
2. **Padrão Transactional Outbox** com PostgreSQL para persistir eventos de negócio e mensagens de saída de forma atômica dentro de uma única transação de banco de dados.
3. **Outbox Poller & Apache Kafka** para distribuição assíncrona e confiável de eventos para serviços consumidores, contando com suporte a **Dead Letter Queue (DLQ)** para falhas não recuperáveis de publicação.

---

## 🛠 Tecnologias Utilizadas

* **Linguagem / Framework**: Java 17, Spring Boot 3.x, Spring Data JPA, Spring Kafka
* **Orquestração de Workflows**: Temporal.io (Java SDK)
* **Banco de Dados & Migração**: PostgreSQL 15, Flyway
* **Mensageria & Streaming**: Apache Kafka, Zookeeper
* **Containerização**: Docker, Docker Compose
* **Observabilidade & Resiliência**: Resilience4j, OpenTelemetry (OTLP)

---

## 🏗 Arquitetura

```
+---------------------------------------------------------------------------------+
|                             HTTP Client / k6 Load Test                          |
+---------------------------------------------------------------------------------+
                                         | HTTP POST /api/v1/checkout
                                         v
+---------------------------------------------------------------------------------+
|                        Spring Boot API (MeliCore Service)                       |
|  - Resilience4j (@CircuitBreaker / Fallbacks)                                  |
|  - Flyway (Migrações de Banco de Dados)                                         |
|  - OpenTelemetry (Traces & Métricas)                                           |
+------------------+------------------------------------+-------------------------+
                   |                                    |
                   v (Transação de Banco)                v (Workflow SAGA)
+------------------------------------+    +---------------------------------------+
|        PostgreSQL Database         |    |              Temporal.io              |
|  - Tabela: orders                  |    |  - Workflow: OrderWorkflow            |
|  - Tabela: outbox_events           |    |  - Activities: ReserveStock,          |
+------------------+-----------------+    |    ProcessPayment, GenerateShipping   |
                   |                      |  - Compensações Automáticas           |
                   | (Polling @Scheduled) +---------------------------------------+
                   v
+------------------------------------+
|          Outbox Poller             |
|  - Lê eventos pendentes            |
|  - Despacha para o Kafka           |
|  - Atualiza `processed = true`     |
+------------------+-----------------+
                   |
                   v
+------------------------------------+
|            Apache Kafka            |
|  - Tópico: orders.events           |
|  - Tópico: orders.dlq (Dead Letter)|
+------------------------------------+
```

### Padrões de Projeto Chave
* **Padrão SAGA (Baseado em Orquestrador)**: Gerenciado pelo Temporal.io. Coordena sequencialmente atividades distribuídas e executa ações de compensação caso qualquer etapa falhe.
* **Padrão Transactional Outbox**: Evita a inconsistência de dados causada por falhas parciais ao gravar no banco de dados e publicar simultaneamente num broker de mensagens.

---

## 📁 Estrutura de Pastas

```text
meli-core-engine/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── melicore/
    │   │           └── engine/
    │   │               ├── CheckoutEngineApplication.java
    │   │               ├── config/
    │   │               │   ├── KafkaTopicConfig.java
    │   │               │   └── TemporalConfig.java
    │   │               ├── controller/
    │   │               │   └── CheckoutController.java
    │   │               ├── domain/
    │   │               │   ├── Order.java
    │   │               │   └── OutboxEvent.java
    │   │               ├── repository/
    │   │               │   ├── OrderRepository.java
    │   │               │   └── OutboxRepository.java
    │   │               ├── scheduler/
    │   │               │   └── OutboxPoller.java
    │   │               └── saga/
    │   │                   ├── OrderWorkflow.java
    │   │                   ├── OrderWorkflowImpl.java
    │   │                   ├── OrderActivities.java
    │   │                   └── OrderActivitiesImpl.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/
    │           └── migration/
    │               └── V1__create_outbox_and_orders_tables.sql
    └── test/
```

---

## 🚀 Guia de Execução Passo a Passo

### Pré-requisitos
* Docker Engine 20.10+ e Docker Compose v2+ instalados.
* `curl` ou qualquer cliente API (Postman, Insomnia).

### 1. Limpar e Subir a Infraestrutura
Compile e inicie todos os serviços containerizados (PostgreSQL, Kafka, Zookeeper, Temporal e Spring Boot):

```bash
# Limpa volumes antigos e sobe o ecossistema completo
docker-compose down -v
docker-compose up --build -d
```

### 2. Verificar a Saúde dos Contentores
Certifique-se de que todos os contentores estão com estado **Up**:

```bash
docker-compose ps
```

Contentores esperados:
* `melicore-checkout-engine` (Porta `8080`)
* `melicore-postgres` (Porta `5432`)
* `melicore-kafka` (Porta `29092`)
* `melicore-zookeeper` (Porta `2181`)
* `melicore-temporal` (Porta `7233`)
* `melicore-temporal-ui` (Porta `8233`)

### 3. Confirmar Criação Automática dos Tópicos no Kafka
Valide se os tópicos `orders.events` e `orders.dlq` foram criados no broker:

```bash
docker exec -it melicore-kafka kafka-topics --bootstrap-server localhost:29092 --list
```

### 4. Executar o Teste do Checkout Ponta a Ponta (E2E)
Dispare uma transação de checkout via `curl`:

```bash
curl -X POST "http://localhost:8080/api/v1/checkout?amount=150.00&customerId=JEAN-BARBOSA&productId=PROD-MELI-01&quantity=2"
```

**Resposta HTTP Esperada:**
```text
SUCCESS - Pedido processado e etiqueta gerada: MELI-LABEL-2992C450
```

---

## 🖥 Visualização de Ordens Processadas (Portal Temporal)

Todas as ordens e workflows SAGA processados podem ser inspecionados em tempo real através do portal Web do **Temporal.io**:

1. Acesse no seu navegador: **`http://localhost:8233`**
2. No menu principal, clique em **Workflows**.
3. Na lista, você encontrará o fluxo `OrderWorkflow` registrado com o **Workflow ID** (ex: `order-1efd1eba-b8b2-4694-80af-407a60fed928`).
4. **Status das Ordens**:
   * **`COMPLETED`**: A ordem foi processada com sucesso por todas as etapas (Estoque $\rightarrow$ Pagamento $\rightarrow$ Envios).
   * **`RUNNING`**: O workflow está atualmente em execução.
   * **`FAILED` / `TERMINATED`**: Ocorreu uma falha no fluxo e as compensações de SAGA foram acionadas.
5. Clique sobre qualquer ID de workflow para visualizar o histórico detalhado de eventos, parâmetros de entrada/saída, tempos de resposta de cada atividade e árvore de execução de compensação.

---

## ⚖ Trade-offs de Arquitetura

| Decisão Arquitetural | Prós | Cons / Trade-offs |
| :--- | :--- | :--- |
| **SAGA via Orquestração (Temporal.io)** | Estado do fluxo explícito, visibilidade centralizada no portal, retentativas nativas, facilidade em compensações. | Dependência de um cluster externo (Temporal Server) e curva de aprendizado para definição de workflows/atividades. |
| **Transactional Outbox via Poller (`@Scheduled`)** | Simplicidade de implementação, não exige plugins adicionais no banco, funciona nativamente com JPA/Flyway. | O polling contínuo gera um leve consumo de CPU e latência (intervalo do agendador). Exige idempotência nos consumidores (entrega *at-least-once*). |
| **Debezium CDC (Alternativa ao Poller)** | Zero overhead de polling no banco de dados, streaming em tempo real via logs WAL do PostgreSQL. | Maior complexidade operacional e necessidade de mais componentes de infraestrutura (Kafka Connect, instâncias do Debezium). |
| **Entrega At-Least-Once (Pelo menos uma vez)** | Garante que nenhum evento seja perdido em momentos de partição de rede ou quedas do broker. | Consumidores de mensagens **devem** ser idempotentes para lidar com eventos duplicados utilizando chaves únicas (UUID do pedido ou do evento). |

---

## 🔍 Resolução de Problemas (Troubleshooting)

### Problema 1: `curl: (7) Failed to connect to localhost:8080`
* **Causa**: A aplicação Spring Boot não iniciou ou ainda está em processo de inicialização no Docker.
* **Solução**: Verifique os logs com `docker-compose logs checkout-engine --tail=50` e aguarde a mensagem `Started CheckoutEngineApplication`.

### Problema 2: `Schema-validation: missing column [processed] in table [outbox_events]`
* **Causa**: A validação do Hibernate falhou porque o volume do PostgreSQL mantinha uma estrutura antiga da tabela sem a coluna `processed`.
* **Solução**: Limpe os volumes antigos do banco e suba a infraestrutura novamente:
  ```bash
  docker-compose down -v
  docker-compose up --build -d
  ```

### Problema 3: `Connection to postgres:5432 refused`
* **Causa**: O container do PostgreSQL ainda não estava pronto para aceitar conexões quando o Spring Boot iniciou o Flyway.
* **Solução**: Reinicie o container da aplicação:
  ```bash
  docker-compose restart checkout-engine
  ```