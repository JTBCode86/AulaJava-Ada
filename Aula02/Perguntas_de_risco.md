# Perguntas de Risco e Engenharia do Caos — Aula 02: APIs Resilientes

> **Contexto de Engenharia:** Análise de cenários de risco, falhas de infraestrutura e comportamento resiliente na integração entre a API de Checkout e o Serviço de Pagamento externo sob carga de 10.000 usuários simultâneos.

---

## 📋 Matriz de Avaliação de Risco & Resiliência

### 1. E se der timeout no retry da integração, corremos o risco de cobrar o cliente duas vezes?

* **Cenário do Problema:** A requisição do pagamento é enviada ao adquirente externo (ex: Cielo/Stripe). O parceiro aprova a transação, mas por instabilidade de rede o pacote de resposta sofre timeout. A política de *Retry* é acionada automaticamente pelo nosso microsserviço e reenvia a requisição.
* **Comportamento e Resposta do Sistema:**
  * **Uso Obrigatório de `Idempotency-Key`:** Todas as chamadas de cobrança enviadas ao gateway externo incluem obrigatoriamente um cabeçalho único de idempotência (`Idempotency-Key: UUIDv7`) vinculado ao pedido.
  * **Tratamento no Gateway:** O parceiro de pagamento reconhece que a chave já foi processada e retorna a resposta da transação original aprovada em vez de processar um novo débito.
  * **Reconciliação Assíncrona:** Caso o limite de retentativas expire sem confirmação, o pedido entra no estado `PENDENTE_DE_CONFIRMACAO` e um *worker* de reconciliação de segundo plano valida o status do pagamento via API de consulta antes de tomar qualquer ação de cancelamento ou estorno.

---

### 2. E se o banco de dados local cair na hora de registrar o estado de falha do pagamento?

* **Cenário do Problema:** A API de pagamento externa responde com erro de saldo insuficiente ou transação recusada, mas o banco de dados transacional da nossa aplicação (PostgreSQL) fica indisponível ou sofre um timeout exatamente no momento do commit da alteração de status do pedido.
* **Comportamento e Resposta do Sistema:**
  * **Isolamento de Estado em Memory Cache (Redis):** O estado temporário da transação e a resposta da chamada externa são mantidos em uma estrutura de cache distribuído de altíssima disponibilidade (Redis Cluster) com *Time-To-Live* (TTL) curto.
  * **Fallbacks com Circuit Breaker & Mensageria:** Se o banco relacional estiver indisponível, o *Circuit Breaker* da persistência é acionado. A mensagem com o estado da falha é enviada para uma fila de processamento assíncrono durável (Kafka/RabbitMQ) para ser persistida no banco assim que ele for restabelecido.
  * **Resposta ao Cliente:** O cliente recebe uma resposta HTTP `202 Accepted` indicando que o processamento do pedido está em andamento, evitando a perda do evento e garantindo que o estado final seja atualizado de forma eventualmente consistente.

---

### 3. E se o Circuit Breaker abrir devido à queda do provedor externo, mas o tráfego de 10.000 usuários continuar chegando na API de Checkout?

* **Cenário do Problema:** O provedor de pagamento principal sai completamente do ar. O *Circuit Breaker* (Resilience4j) transiciona para o estado `OPEN` para evitar o esgotamento das Virtual Threads. Porém, milhares de requisições de novos clientes continuam atingindo o endpoint `/checkout`.
* **Comportamento e Resposta do Sistema:**
  * **Resposta Imediata via Fallback (Fast-Fail):** Com o circuito aberto, as chamadas para o parceiro são interrompidas instantaneamente sem consumo de rede ou timeouts, liberando os recursos do servidor em poucos milissegundos.
  * **Estratégia de Pagamento Alternativo:** O método de *fallback* altera dinamicamente o fluxo do checkout, oferecendo formas de pagamento alternativas que não dependem do parceiro indisponível (ex: emissão de PIX estático via arranjo secundário ou boleto bancário).
  * **Controle de Vazão (RateLimiting & Degradação Graciosa):** O *RateLimiter* na borda (API Gateway) restringe requisições abusivas e apresenta uma página ou mensagem amigável de alta demanda, protegendo os serviços internos enquanto a dependência externa recupera a estabilidade.