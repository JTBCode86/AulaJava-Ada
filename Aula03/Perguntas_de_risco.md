# 🧪 Cenários de Risco & Engenharia do Caos (Checkout & Pagamentos)

Mapeamento de cenários críticos de falha sob a perspectiva da Engenharia do Caos, identificando o comportamento esperado do sistema e as estratégias de mitigação técnica para cada risco.

---

## ❓ Pergunta de Risco 1
> **"E se a chamada ao gateway der timeout, mas a cobrança tiver sido processada com sucesso do lado deles?"**

### ⚠️ Impacto sem Proteção:
A nossa aplicação assume que a transação falhou devido ao tempo limite atingido. Ao acionar um *retry* automático ou permitir que o usuário refaça a tentativa na interface, o cliente é cobrado em duplicidade.

### 🛡️ Estratégia de Mitigação:
* **Chave de Idempotência Obrigatória:** Envio do cabeçalho `X-Idempotency-Key` em todas as tentativas para o gateway parceiro (Cielo, Stripe, etc.). O parceiro reconhecerá a chave e retornará a resposta do pagamento já efetuado sem reprocessar o cartão.
* **Consulta de Conciliação (*Polling/Reconciliation*):** Antes de executar qualquer retentativa após um *timeout*, o sistema realiza uma chamada de verificação de estado (`GET /v1/payments/{paymentId}`) na API do parceiro.

---

## ❓ Pergunta de Risco 2
> **"E se a mensagem de confirmação do pagamento falhar na publicação no Kafka/SQS ou a base de dados cair logo após o gateway aprovar a cobrança?"**

### ⚠️ Impacto sem Proteção:
O dinheiro é debitado do cliente no fornecedor de pagamento, mas a nossa aplicação não consegue atualizar o estado interno do pedido para `PAID` nem notificar os microsserviços do ecossistema. O cliente é cobrado, mas não recebe o produto/serviço.

### 🛡️ Estratégia de Mitigação:
* **Transactional Outbox Pattern:** O evento de confirmação é gravado na tabela `outbox` dentro da mesma transação local da base de dados do pedido. Um processo assíncrono (*Debezium/CDC* ou *Relay Service*) lê a tabela e garante a entrega da mensagem no *message broker* (*At-Least-Once Delivery*).
* **Mapeamento de Webhooks Assíncronos:** Configuração de um endpoint de callback para receber e processar eventos de notificação passiva diretamente do gateway de pagamento, garantindo a eventual consistência da transação.

---

## ❓ Pergunta de Risco 3
> **"E se o Redis (usado para controle de idempotência e locks) ficar indisponível exatamente durante um pico de tráfego?"**

### ⚠️ Impacto sem Proteção:
Sem o Redis para verificar chamadas duplicadas simultâneas, requisições idênticas enviadas por instabilidades na rede chegam diretamente ao gateway, gerando duplicidade de cobranças e sobrecarga na base de dados principal.

### 🛡️ Estratégia de Mitigação:
* **Restrição de Unicidade no Banco de Dados (`UNIQUE Constraint`):** Definição de índice único no campo `idempotency_key` da tabela relacional/NoSQL como camada secundária de defesa (*Defense in Depth*). Caso o Redis falhe, a tentativa de inserção duplicada falha na própria base de dados (`UniqueConstraintViolationException`).
* **Abordagem Fail-Closed:** Em fluxos críticos de liquidação financeira, caso a camada distribuída de concorrência/lock fique totalmente inacessível, a API responde `503 Service Unavailable` em vez de arriscar o processamento sem proteções ativas.