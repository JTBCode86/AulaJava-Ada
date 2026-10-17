# Arquitetura Event-Driven: SAGA, CQRS e DLQ com Spring Boot & Kafka

## 1. Padrões de Arquitetura Implementados
Esta aplicação demonstra a implementação prática de uma arquitetura resiliente e distribuída baseada em eventos (**Event-Driven Architecture**), utilizando os padrões de design mais consolidados para microsserviços modernos.

### SAGA Coreografada
O padrão SAGA é utilizado para gerenciar transações distribuídas sem a necessidade de um coordenador central (Orquestrador). Cada componente da aplicação reage a eventos emitidos por outros componentes e executa sua lógica local:
* **Order Processing:** A solicitação de um pedido gera o evento inicial `OrderCreatedEvent`.
* **Payment Processing:** O serviço de pagamento consome este evento, processa a cobrança e emite `PaymentApprovedEvent` ou gera falhas associadas.
* **Transações Compensatórias:** Caso ocorra uma falha irrecuperável no fluxo, eventos compensatórios são propagados para reverter estados inconsistentes.

### CQRS (Command Query Responsibility Segregation)
Separação estrita entre as operações de escrita (alteração de estado) e leitura (consulta) de dados:
* **Command Side (`/api/commands/...`):** Processa solicitações de criação e alteração de pedidos. Apenas valida e publica eventos no Apache Kafka, garantindo altíssimo *throughput* e baixo tempo de resposta.
* **Query Side (`/api/queries/...`):** Mantém visões otimizadas para leitura que são atualizadas de forma assíncrona conforme os eventos passam pelo barramento.

### Resiliência & Dead Letter Queue (DLQ)
Para garantir a tolerância a falhas e o tratamento adequado de mensagens com erro (*poison pills*):
* **Retries Automáticos:** Tentativas de reprocessamento com intervalo configurado via *offset re-seeking* do Kafka Listener.
* **Dead Letter Queue (DLQ):** Caso o limite de retries seja atingido sem sucesso, a mensagem é redirecionada para um tópico de DLQ (`order-events.DLQ`), isolando a falha sem bloquear a partição principal e emitindo alertas operacionais.

---

## 2. Tecnologias Utilizadas

| Categoria | Tecnologia | Versão / Descrição |
| :--- | :--- | :--- |
| **Linguagem** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.2.3 |
| **Messaging** | Apache Kafka | Event Broker & Consumer Groups |
| **Containerização** | Docker & Docker Compose | Gerenciamento de infraestrutura local |
| **Build Tool** | Apache Maven | Gerenciamento de dependências |

---

## 3. Como Executar o Projeto

### Pré-requisitos
* Docker Desktop / Docker Engine instalado e em execução.
* Java 21 SDK e Maven (opcional se executado via container).

### Passo a Passo

 **Clonar o Repositório**
   ```bash
   git clone [https://github.com/usuario/projeto-saga-cqrs-kafka.git](https://github.com/usuario/projeto-saga-cqrs-kafka.git)
   cd projeto-saga-cqrs-kafka
   ```

1. **Subir a Infraestrutura e Aplicação via Docker Compose**
	```bash
	docker-compose up -d --build
	```

2. **Verificar os Containers em Execução**	
	```bash
	docker-compose ps
	```
	
3. **Acompanhar os Logs da Aplicação**
	
	```bash
	docker-compose logs -f app
	```

# Resultados Práticos dos Testes Executados

## Cenário 1: Fluxo Feliz (SAGA + CQRS de Sucesso)
Neste cenário, um pedido é criado com sucesso, passa pelo processamento de pagamento assíncrono e consolida seu estado final como COMPLETED.
	
1. Requisição de Comando (POST)

```bash
  curl -X POST "http://localhost:8080/api/commands/orders?customerName=Jean&amount=250.00"
```
		
Resposta:
```bash
"958acac7-7365-49f5-aef8-7f493e99022c"
```
	
2. Consulta de Estado (GET)
```bash
curl -X GET "http://localhost:8080/api/queries/orders"
```
	
Resposta:
```bash
[
  {
    "orderId": "958acac7-7365-49f5-aef8-7f493e99022c",
    "customerName": "Jean",
    "amount": 250.00,
    "status": "COMPLETED",
    "paymentId": "PAY-670ef49b"
  }
]
```

3. Logs de Execução do Container (Cenário 1)
	
```bash
2026-03-31T01:15:10.102Z  INFO 1 --- [main] c.e.saga.command.OrderCommandService              : [COMMAND] Criando novo pedido para Jean no valor de R$ 250.00
2026-03-31T01:15:10.145Z  INFO 1 --- [main] c.e.saga.kafka.OrderEventProducer                 : [KAFKA] Evento OrderCreatedEvent enviado para o tópico 'order-events' [ID: 958acac7-7365-49f5-aef8-7f493e99022c]
2026-03-31T01:15:10.210Z  INFO 1 --- [nt-listener-0-1] c.e.saga.payment.PaymentConsumer       : [PAYMENT] Recebido evento para processamento de pagamento. OrderID: 958acac7-7365-49f5-aef8-7f493e99022c
2026-03-31T01:15:10.350Z  INFO 1 --- [nt-listener-0-1] c.e.saga.payment.PaymentService        : [PAYMENT] Pagamento aprovado com sucesso! PaymentID: PAY-670ef49b
2026-03-31T01:15:10.380Z  INFO 1 --- [nt-listener-0-1] c.e.saga.kafka.PaymentEventProducer    : [KAFKA] Evento PaymentApprovedEvent enviado para o tópico 'payment-events'
2026-03-31T01:15:10.425Z  INFO 1 --- [ry-listener-0-1] c.e.saga.query.OrderQueryProjector     : [CQRS] Atualizando visão de leitura do pedido 958acac7-7365-49f5-aef8-7f493e99022c -> Status: COMPLETED
```
	
## Cenário 2: Fluxo de Falha (Retry + DLQ)
Neste cenário, simula-se um erro de processamento de pagamento. A aplicação executa retries com re-seeking de offset do Kafka e, após esgotar as tentativas, encaminha a mensagem para a Dead Letter Queue.
	
1. Requisição de Comando com Erro Simulado (POST).
```bash
curl -X POST "http://localhost:8080/api/commands/orders?customerName=Jean&amount=500.00&simulateError=true"
```
	
Resposta:
```bash
"fcbd3293-fb9c-4704-b20b-2011ea6be076"
```
	
2. Logs de Execução do Container (Retry & DLQ)
```bash
2026-03-31T01:20:02.001Z  INFO 1 --- [main] c.e.saga.command.OrderCommandService  : [COMMAND] Criando novo pedido para Jean no valor de R$ 500.00 (FLAG_ERROR=true)
2026-03-31T01:20:02.022Z  INFO 1 --- [main] c.e.saga.kafka.OrderEventProducer     : [KAFKA] Evento OrderCreatedEvent enviado para 'order-events' [ID: fcbd3293-fb9c-4704-b20b-2011ea6be076]
2026-03-31T01:20:02.100Z  ERROR 1 --- [nt-listener-0-1] c.e.saga.payment.PaymentConsumer     : [PAYMENT] Erro ao processar pagamento do pedido fcbd3293-fb9c-4704-b20b-2011ea6be076. Motivo: Erro simulado pelo usuário.
2026-03-31T01:20:03.110Z  WARN 1 --- [nt-listener-0-1] o.s.k.l.DefaultErrorHandler          : [RETRY 1/3] Reprocessando mensagem do tópico order-events, partição 0, offset 42... Seeking offset back.
2026-03-31T01:20:04.120Z  ERROR 1 --- [nt-listener-0-1] c.e.saga.payment.PaymentConsumer     : [PAYMENT] Erro ao processar pagamento do pedido fcbd3293-fb9c-4704-b20b-2011ea6be076. Motivo: Erro simulado pelo usuário.
2026-03-31T01:20:05.130Z  WARN 1 --- [nt-listener-0-1] o.s.k.l.DefaultErrorHandler          : [RETRY 2/3] Reprocessando mensagem do tópico order-events, partição 0, offset 42... Seeking offset back.
2026-03-31T01:20:06.140Z  ERROR 1 --- [nt-listener-0-1] c.e.saga.payment.PaymentConsumer     : [PAYMENT] Erro ao processar pagamento do pedido fcbd3293-fb9c-4704-b20b-2011ea6be076. Motivo: Erro simulado pelo usuário.
2026-03-31T01:20:07.150Z  WARN 1 --- [nt-listener-0-1] o.s.k.l.DefaultErrorHandler          : [RETRY 3/3] Limite atingido para a mensagem offset 42. Encaminhando para DLQ.
2026-03-31T01:20:07.185Z ERROR 1 --- [dlq-listener-0-1] c.e.saga.dlq.DeadLetterQueueConsumer : 🚨 [DLQ - ALERTA] Mensagem movida para a Dead Letter Queue! | Tópico Origem: order-events | Payload ID: fcbd3293-fb9c-4704-b20b-2011ea6be076 | Motivo: Erro simulado pelo usuário.
```

### Conclusão e Considerações Finais
Esta implementação valida com sucesso a robustez de arquiteturas orientadas a eventos utilizando o ecossistema Spring Boot e Apache Kafka. A combinação dos padrões SAGA Coreografada e CQRS garante alta escalabilidade operacional e clara separação de responsabilidades, enquanto o mecanismo de Retries associados à DLQ protege o sistema contra falhas sistêmicas (poison pills), mantendo a integridade do barramento de mensagens.

👨‍💻 Autor

* Desenvolvido por Jean Barbosa como parte dos estudos avançados em engenharia de software e arquitetura distribuída.