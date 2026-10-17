# Perguntas de Risco & Análise de Falhas em Transações Distribuídas (Aula 04)

> **Cenário de Engenharia:** Marketplace de Alta Vazão (5.000 TPS) na Black Friday com bancos autônomos por microsserviço (Checkout, Pagamento e Estoque).

---

## ❓ Pergunta de Risco 1: E se a chamada de compensação (estorno Pix) falhar por instabilidade no parceiro bancário?

### 🔴 O Risco de Engenharia:
Após o pagamento ser aprovado via Pix e o serviço de estoque rejeitar a reserva por produto esgotado, o Orquestrador dispara o estorno. Porém, o Gateway de Pagamentos ou a API do Banco Central retorna timeout ou erro HTTP 503 na chamada de estorno. O cliente pagou R$ 3.000, não tem a TV e a transação de estorno falhou.

### 🛡️ Comportamento Arquitetural Esperado:
1. **Transição de Estado Transiente:** O pedido transiciona para o estado `ESTORNO_PENDENTE`.
2. **Retentativa com Backoff Exponencial & Jitter:** O consumidor/orquestrador re-enfileira a mensagem de estorno em tópicos de retry com intervalos crescentes (ex: 5s, 30s, 5m, 15m) para dar tempo de recuperação ao parceiro bancário.
3. **Dead Letter Queue (DLQ) & Alerta de Alta Severidade:** Se após o limite de tentativas o estorno não for concluído, a mensagem é movida para uma DLQ de compensação e um alerta P1 é gerado no PagerDuty/Observabilidade.
4. **Worker de Conciliação Automática:** Um job em segundo plano reprocessa ciclicamente itens da DLQ ou faz conciliação via extrato bancário (Open Finance / Webhooks de retorno). O cliente **nunca** fica sem o valor de volta.

---

## ❓ Pergunta de Risco 2: E se o broker de mensageria (Kafka) estiver fora do ar no exato momento em que o Checkout salva o pedido no PostgreSQL?

### 🔴 O Risco de Engenharia:
Se o microsserviço de Checkout gravar o pedido na sua base local com sucesso e tentar publicar o evento `PedidoCriadoEvent` diretamente no Kafka via rede, uma falha de conexão com o broker fará a operação lançar exceção. Se o desenvolvedor fizer *catch* e ignorar, o pedido fica órfão. Se fizer rollback no banco, causa latência e perde a transação que poderia ter sido salva.

### 🛡️ Comportamento Arquitetural Esperado:
1. **Eliminação do Dual Write:** O microsserviço de Checkout **nunca** faz chamada direta de rede ao Kafka dentro do fluxo de criação do pedido.
2. **Transactional Outbox Pattern:** O pedido e o evento da outbox (`OutboxEvent`) são salvos no PostgreSQL dentro da **mesma transação ACID local** (`@Transactional`).
3. **Despacho Assíncrono via CDC:** O conector Debezium lê o log de transações (*WAL*) do Postgres de forma assíncrona e garante a publicação no Kafka (*At-Least-Once Delivery*). Se o Kafka estiver fora, as mensagens acumulam no banco/WAL e são despachadas assim que o broker retornar, sem perda de dados.

---

## ❓ Pergunta de Risco 3: E se o cliente receber a confirmação no checkout e atualizar a tela em "Meus Pedidos" antes da projeção CQRS ser atualizada?

### 🔴 O Risco de Engenharia:
A escrita do pedido é processada no banco relacional de Checkout, enquanto a leitura da tela "Meus Pedidos" é servida por uma base otimizada para consulta (Elasticsearch / Redis / Read Replica) sincronizada por eventos assíncronos. Se o sincronismo levar 200ms e o cliente recarregar a tela em 100ms, ele verá a lista vazia e achará que a compra falhou, tentando comprar novamente.

### 🛡️ Comportamento Arquitetural Esperado:
1. **Optimistic UI Update no Client:** O aplicativo/frontend inclui temporariamente o novo pedido no estado local da interface com o badge "Processando" usando o `pedido_id` retornado pela API de escrita.
2. **Leitura Direta por ID (Read-Your-Own-Writes):** Consultas específicas pelo ID recém-criado nos primeiros segundos são direcionadas temporariamente para o cache rápido ou endpoint de escrita antes de recorrer à visão materializada.
3. **Atualização Reativa via WebSockets / Server-Sent Events (SSE):** O frontend mantém um canal aberto de comunicação assíncrona e recebe o evento de confirmação de estoque/pagamento em tempo real, atualizando a UI sem necessidade de reload manual (*polling*).