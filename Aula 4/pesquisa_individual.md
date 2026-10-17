# 🔬 Pesquisa Individual: Microsserviços, Sagas & Consistência Distribuída

> **Aula 04 - Java PE-JV-003 | Ada Tech**
> **Tema:** Transações Distribuídas, Padrão Saga, Pattern Outbox/CDC e CQRS em Sistemas de Alta Vazão (5.000 TPS).

---

## 1. A Morte do 2PC (Two-Phase Commit)

### ❓ Por que Two-Phase Commit não escala sob alto throughput? Em qual volume ainda é válido?

* **O Problema do Bloqueio Distribuído:** O protocolo 2PC exige um coordenador central que bloqueia (*lock*) registros em todos os bancos de dados participantes durante a fase de preparação e confirmação. Em um cenário de **5.000 TPS**, a latência de rede entre os nós mantém os locks abertos por centenas de milissegundos, causando rápido esgotamento de conexões e *thread starvation*.
* **Ponto Único de Falha e Latência Escalável:** Se um participante ou o coordenador oscilar, a transação inteira fica travada segurando locks em múltiplos bancos. A latência final da transação equivale à soma da pior latência de cada participante.
* **Quando ainda é válido?** O 2PC é válido em baixíssima volumetria (ex: < 50 TPS), ambientes monolíticos com múltiplos schemas no mesmo SGBD, ou em transações financeiras internas onde a consistência forte e imediata suplanta a necessidade de escalabilidade horizontal.

---

## 2. Coreografia vs. Orquestração no Padrão Saga

### ❓ Em que momento a Coreografia vira "espaguete de eventos" e quando a Orquestração vira um monólito centralizador?

* **Coreografia (Orientada a Eventos Reativos):**
  * *Quando vira Espaguete:* À medida que o fluxo cresce (ex: Checkout -> Pagamento -> Estoque -> NF-e -> Logística -> Anti-fraude), a lógica de controle fica pulverizada em escuta de tópicos. Fica inviável rastrear em tempo real o estado de um pedido sem ferramentas complexas de rastreamento distribuído (*Distributed Tracing*), surgindo dependências cíclicas e loops de eventos difíceis de depurar.
* **Orquestração (Process Manager / Máquina de Estados):**
  * *Quando vira Monólito:* Quando o Orquestrador passa a conter regras de negócio que pertencem aos serviços participantes (ex: calcular regra do imposto da NF-e ou validar regra de cartão). Ele deve apenas *coordenar* estados e invocar comandos, delegando toda a inteligência aos microsserviços.

---

## 3. Compensação Sem Rollback & Consumidores Idempotentes

### ❓ O que é uma transação compensatória e por que o consumidor precisa ser idempotente?

* **Transação Compensatória:** Em bancos autônomos sem `@Transactional` global, o *rollback* físico é impossível após o commit local de um passo. A compensação é uma ação explícita de negócio (ex: estorno financeiro, devolução de voucher) que reverte semanticamente o efeito de um passo anterior concluído.
* **Necessidade de Idempotência:** Devido ao modelo de entrega de mensagens *At-Least-Once* de brokers como Kafka e RabbitMQ, um evento de compensação pode ser entregue mais de uma vez ao serviço de pagamento. O consumidor deve usar chaves de idempotência para garantir que duas entregas da mesma mensagem resultem em apenas um único estorno.

---

## 4. Dual Write & Transactional Outbox Pattern

### ❓ O que acontece no Dual Write e como o Pattern Outbox resolve o problema?

* **O Problema do Dual Write:** Tentar atualizar o banco de dados e publicar uma mensagem no Kafka na mesma linha de código da aplicação (`repository.save()` seguido de `kafkaTemplate.send()`). Se a rede ou o Kafka falharem imediatamente após o commit do banco, o evento é perdido para sempre, deixando o sistema inconsistente.
* **A Solução com Transactional Outbox + CDC:**
  1. A aplicação grava a entidade de negócio e a intenção do evento na tabela `outbox` do banco dentro da **mesma transação ACID local**.
  2. Um processo independente de *Change Data Capture* (ex: Debezium) lê o log de transações do banco (*WAL*) e publica o evento no Kafka de forma assíncrona.
  3. Isso garante garantia de entrega sem chamadas diretas de rede bloqueantes durante a escrita principal.

---

## 5. CQRS & Consistência Eventual na Black Friday

### ❓ Por que separar a escrita (Command) da consulta (Query) protege o sistema?

* **Proteção do Pipeline de Vendas:** No pico da Black Friday, o volume de consultas (clientes recarregando "Meus Pedidos") é até 100x maior que o volume de checkout. Se ambas usarem o mesmo banco, as consultas pesadas de `SELECT` com `JOINs` consumirão CPU e conexões necessárias para os `INSERTs` e `UPDATEs` de vendas.
* **Separação por CQRS:** 
  * O modelo de **Escrita** foca em consistência transacional local (PostgreSQL isolado).
  * O modelo de **Leitura** é alimentado por eventos assíncronos e serve consultas em bases otimizadas (Redis, Elasticsearch ou Read Replicas).
* **Tratamento da Consistência Eventual:** O atraso de milissegundos no sincronismo entre escrita e leitura é mitigado no frontend através de atualização otimista da interface (*Optimistic UI*) ou consumo de mensagens via WebSocket/SSE.