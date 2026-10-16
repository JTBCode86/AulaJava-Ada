# Documento de System Design: Transações Distribuídas & Resiliência em Alta Escala

> **Escopo do Documento:**
> Este documento especifica a arquitetura de solução para o **Incidente da Black Friday**, garantindo consistência transacional entre os serviços autônomos de **Checkout**, **Pagamento** e **Estoque** sob altíssimo throughput de acessos concorrentes.

---

## 🗺️ Matriz de Mapeamento: Problemas do Incidente vs. Solução Arquitetural

| Problema Identificado no Incidente | Padrão Arquitetural / Tecnologia | Referência de Laboratório Oficial |
| :--- | :--- | :--- |
| **Estoque esgotado após débito confirmado (Inconsistência)** | Pattern Saga com Transações Compensatórias | [Temporal Booking SAGA](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/bookingsaga) |
| **Incapacidade de escalar transações síncronas sob pico de carga** | Saga por Orquestração Assíncrona | [Temporal Money Transfer](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/moneytransfer) |
| **Perda de eventos / falha de rede na publicação do broker** | Transactional Outbox + CDC | [Debezium Outbox Pattern](https://github.com/debezium/debezium-examples/tree/main/outbox) |
| **Processamento duplicado em retentativas do cliente** | Idempotência no Consumidor (Idempotency Key) | [Confluent Spring Boot & Kafka](https://developer.confluent.io/get-started/spring-boot/) |
| **Leituras massivas no banco transacional durante pesquisas** | CQRS (Command Query Responsibility Segregation) | [Confluent CQRS](https://developer.confluent.io/courses/event-sourcing/cqrs/) |

---

## 1. Transações Distribuídas & Coordenação

### 1.1. A Morte do 2PC (Two-Phase Commit) em Alta Volumetria

#### Por que o 2PC falha na Black Friday:
1. **Bloqueio Síncrono (*Blocking Protocol*):** O protocolo Two-Phase Commit exige que os gerenciadores de transação de todos os bancos de dados envolvidos (Checkout, Pagamento, Estoque) travem (*lock*) as linhas necessárias durante a fase de *Prepare*. Sob um volume de milhares de requisições por segundo, o tempo de permanência dessas travas abertas gera contenção severa de E/S, estouro de conexões e *deadlocks* sistêmicos.
2. **Degradação do Teorema CAP:** Em um sistema distribuído, o 2PC prioriza a Consistência Imediata em detrimento da Disponibilidade e Tolerância a Partições. Se a rede entre o serviço de Pagamentos e o serviço de Estoque oscilar por alguns milissegundos na fase de *Commit*, todas as transações em andamento são bloqueadas indefinidamente, derrubando o checkout do marketplace.

#### Cenários onde o 2PC ainda é aplicável:
- Operações de baixíssima volumetria restritas a um único cluster de banco de dados distribuído nativo que implementa *Raft* ou *Paxos* (ex: CockroachDB, Google Cloud Spanner) e dentro da mesma fronteira de rede local.

---

### 1.2. Modelo de Coordenação da Saga: Orquestração vs. Coreografia

#### Decisão Arquitetural: **Saga Baseada em Orquestração**

```
 ┌──────────────────────────────────────────────────────────────┐
 │                      Serviço Checkout                        │
 └──────────────────────────────┬───────────────────────────────┘
                                │ Inicia Pedido
                                ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                 Orquestrador de Pedidos                      │
 │                 (Saga Orchestrator)                          │
 └───────┬──────────────────────┬───────────────────────┬───────┘
         │ (1) Reservar         │ (2) Cobrar            │ (3) Notificar
         │     Estoque          │     Pagamento         │     Cliente
         ▼                      ▼                       ▼
 ┌───────────────┐      ┌───────────────┐       ┌───────────────┐
 │  Serviço de   │      │  Serviço de   │       │  Serviço de   │
 │   Estoque     │      │   Pagamento   │       │ Notificação   │
 └───────────────┘      └───────────────┘       └───────────────┘
```

#### Análise Comparativa e Racional da Escolha:

- **Por que NÃO Coreografia no Marketplace?**
  Na coreografia, cada microsserviço reage a eventos e publica novos eventos (ex: `PedidoCriado` $\rightarrow$ `PagamentoEfetuado` $\rightarrow$ `EstoqueReservado`). Conforme o fluxo do checkout se expande para incluir cupons, antifraude, emissão de nota fiscal e logística, a malha de dependências gera um "espaguete de eventos" (*event spaghetti*). O rastreamento do estado global do pedido torna-se extremamente complexo, dificultando o diagnóstico de falhas parciais e auditorias financeiras.

- **Por que Orquestração?**
  Centraliza o fluxo do negócio em uma máquina de estados explícita e auditável (mecanismo similar ao *Temporal.io*). O orquestrador sabe exatamente em qual etapa o pedido se encontra e qual ação compensatória deve ser acionada caso ocorra algum erro.

- **Mitigação do Risco do Orquestrador Virar um Monolito / Ponto Único de Falha:**
  1. **Stateless e Escalável Horizontalmente:** O motor do orquestrador não guarda estado em memória RAM local. Cada transição de estado é persistida atomicamente em banco de dados ou log de eventos durável.
  2. **Isolamento de Domínio:** O orquestrador limita-se a coordenar o fluxo transacional (tomada de decisão baseada em regras de estado); ele não executa regras de negócio internas das pontas (como cálculo de imposto ou regras do gateway de pagamento).

---

## 2. Máquina de Estados, Compensação e Atomicidade

### 2.1. Estados do Pedido e Mapeamento do Fluxo

#### Caminho Feliz (*Happy Path*):
$$\text{PEDIDO\_CRIADO} \longrightarrow \text{RESERVA\_ESTOQUE\_SOLICITADA} \longrightarrow \text{ESTOQUE\_RESERVADO} \longrightarrow \text{PAGAMENTO\_SOLICITADO} \longrightarrow \text{PAGAMENTO\_CONFIRMADO} \longrightarrow \text{PEDIDO\_CONCLUIDO}$$

#### Caminho de Compensação (*Compensating Path* - Falha de Estoque):
Se o pagamento for aprovado mas a reserva de estoque falhar (produto esgotado no milissegundo final):

$$\text{PAGAMENTO\_CONFIRMADO} \longrightarrow \text{FALHA\_RESERVA\_ESTOQUE} \longrightarrow \text{SOLICITANDO\_ESTORNO\_PAGAMENTO} \longrightarrow \text{PAGAMENTO\_ESTORNADO} \longrightarrow \text{PEDIDO\_CANCELADO}$$

```
 [PEDIDO_CRIADO]
        │
        ▼
 [RESERVA_ESTOQUE_SOLICITADA] ──(Falha)──► [PEDIDO_CANCELADO]
        │ (Sucesso)
        ▼
 [PAGAMENTO_SOLICITADO]
        │
        ├───────────────────────┐
    (Sucesso)                (Falha)
        │                       │
        ▼                       ▼
 [PAGAMENTO_CONFIRMADO]    [SOLICITANDO_LIBERACAO_ESTOQUE]
        │                       │
        │                       ▼
        │                  [ESTOQUE_LIBERADO] ──► [PEDIDO_CANCELADO]
        ▼
 [ENTREGA_SOLICITADA]
        │
     (Falha)
        │
        ▼
 [SOLICITANDO_ESTORNO_PAGAMENTO]
        │
        ▼
 [PAGAMENTO_ESTORNADO] ──► [SOLICITANDO_LIBERACAO_ESTOQUE] ──► [PEDIDO_CANCELADO]
```

---

### 2.2. Compensação sem Rollback: Tratamento do Cliente e DLQ

Quando uma transação é concluída em um microsserviço (ex: valor debitado no Pix via BACEN), não existe *rollback* físico de banco de dados para desfazer a alteração em outro serviço. A reversão é obrigatoriamente semântica (Transação Compensatória).

#### Estratégia para evitar prejuízo ao cliente no estorno:
1. **Retentativas com Exponential Backoff e Jitter:** Chamadas de estorno ao gateway/banco usam política de retentativa automática parametrizada.
2. **Dead Letter Queue (DLQ) para Compensações:** Se todas as retentativas do estorno falharem por indisponibilidade externa prolongada, o evento de compensação é enviado para uma DLQ isolada (`dlq-pagamento-compensacao`).
3. **Worker de Reconciliação Passiva (Self-Healing):** Um serviço de segundo plano consome a DLQ periodicamente, acionando APIs secundárias do adquirente ou gerando automaticamente um vale-compras/alerta priorizado para o time financeiro resolver a pendência em até 1 hora.

---

### 2.3. Eliminação do Dual Write via Transactional Outbox Pattern

O problema do *Dual Write* ocorre quando uma aplicação tenta gravar no banco de dados e publicar uma mensagem no broker de mensagens em duas operações separadas. Se o sistema falhar entre as duas instruções, ocorre inconsistência de dados.

```
                        ┌──────────────────────────────────────────────┐
                        │              Microsserviço                   │
                        └──────────────────────┬───────────────────────┘
                                               │
                                               │ 1. Transação Local ACID
                                               ▼
                        ┌──────────────────────────────────────────────┐
                        │ BANCO DE DADOS LOCAL (Ex: PostgreSQL)        │
                        │                                              │
                        │  ┌──────────────────┐  ┌──────────────────┐  │
                        │  │  Tabela Pedido   │  │  Tabela Outbox   │  │
                        │  └──────────────────┘  └──────────────────┘  │
                        └──────────────────────┬───────────────────────┘
                                               │
                                               │ 2. Leitura do WAL (CDC)
                                               ▼
                        ┌──────────────────────────────────────────────┐
                        │          Engine CDC (Ex: Debezium)           │
                        └──────────────────────┬───────────────────────┘
                                               │
                                               │ 3. Publicação At-Least-Once
                                               ▼
                        ┌──────────────────────────────────────────────┐
                        │          Message Broker (Apache Kafka)       │
                        └──────────────────────────────────────────────┘
```

#### Funcionamento da Solução:
1. **Gravação Atômica Local:** O serviço de Checkout insere o registro na tabela `pedidos` e a representação do evento na tabela `outbox_events` dentro da **mesma transação relacional** (`@Transactional`). Se a transação falhar, nada é gravado.
2. **Captura por Change Data Capture (CDC):** Uma ferramenta de CDC (como o Debezium) monitora os logs de transação (*Write-Ahead Log - WAL* do PostgreSQL) e lê as novas inserções da tabela `outbox_events`.
3. **Entrega para o Broker:** O Debezium publica a mensagem no tópico correspondente do Apache Kafka de forma assíncrona.

#### Garantia de Idempotência no Consumidor:
Como o mecanismo de CDC garante entrega no formato *At-Least-Once* (Pelo Menos Uma Vez), mensagens duplicadas podem chegar aos consumidores. Cada microsserviço consumidor deve implementar um **Filtro de Idempotência**:
- Antes de processar a mensagem, o consumidor verifica em um cache distribuído (Redis) ou tabela local se o `event_id` (UUIDv7) já foi processado.
- Caso já tenha sido processado, a mensagem é descartada imediatamente sem reprocessar a regra de negócio.

---

## 3. CQRS & Testes Transversais de Falha

### 3.1. Desacoplamento com CQRS (Command Query Responsibility Segregation)

Durante a Black Friday, a relação entre consultas de status de pedidos e criação de novos checkouts pode chegar a uma proporção de $100:1$. Tentar responder a todas as consultas na mesma base transacional afeta a performance das escritas do checkout.

```
 [Cliente / Mobile App]
    │
    ├────────── Command (POST /checkout) ──────────► [API de Escrita] ──► [Postgres OLTP]
    │                                                                           │
    │                                                                    Outbox / CDC
    │                                                                           │
    │                                                                           ▼
    │                                                                     [Kafka Topic]
    │                                                                           │
    └────────── Query (GET /meus-pedidos) ─────────► [API de Consulta]             │
                                                           ▲                    │
                                                           │ Projeção Assíncrona│
                                                           └────────────────────┘
                                                                    │
                                                            [Elasticsearch / Redis]
```

#### Arquitetura Proposta:
* **Escrita (Command):** Focada em validar regras de negócio, garantir consistência ACID local e gravar a intenção de compra. Utiliza modelo de dados normalizado no PostgreSQL.
* **Leitura (Query):** Utiliza réplicas otimizadas para leitura (Elasticsearch ou Redis) com dados desnormalizados e indexados.
* **Gestão da Consistência Eventual na Interface (UX):**
  - Após finalizar a compra, a API de Checkout responde HTTP 202 (Accepted) com a estrutura inicial do pedido e o status `EM_PROCESSAMENTO`.
  - A interface gráfica exibe o status de processamento imediatamente via **Optimistic UI**.
  - A atualização em tempo real para `PAGAMENTO_CONFIRMADO` ocorre no front-end por meio de conexões persistentemente mantidas via **Server-Sent Events (SSE)** ou **WebSockets**, liberando o banco de escrita de sofrer *polling* contínuo.

---

### 3.2. Estratégia de Testes de Falha e Validação Assíncrona

Para homologar a resiliência do ambiente antes da entrada em produção, o pipeline de CI/CD e o ambiente de testes integram os seguintes testes de engenharia de caos:

1. **Simulação de Falhas de Comunicação com Toxiproxy / Chaos Mesh:**
   - **Injeção de Latência:** Injetar 3000ms de atraso nas chamadas ao gateway de pagamento durante a Saga para validar o acionamento de retentativas assíncronas e estouro de *timeouts* controlados.
   - **Partição de Rede:** Bloquear o tráfego entre o banco de dados de Estoque e o broker Kafka para validar se o *Transactional Outbox* acumula os eventos em disco sem perda de mensagens.
2. **Testes de Idempotência e Eventos Duplicados:**
   - Enviar a mesma mensagem de `PagamentoConfirmadoEvent` três vezes seguidas para o tópico do Kafka e verificar se o estoque é reduzido apenas uma vez.
3. **Validação de Recuperação na DLQ:**
   - Forçar uma falha sintática (*poison pill*) em uma mensagem de compensação e verificar se ela é movida com sucesso para a DLQ sem travar o processamento dos demais itens da partição.