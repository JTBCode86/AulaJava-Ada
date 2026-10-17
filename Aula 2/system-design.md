# System Design: Aula 02 - APIs Resilientes e Integração de Checkout

> **Contexto:** Projeto de integração da API de Checkout com gateway de pagamento externo sob alta concorrência (10.000 usuários simultâneos)[cite: 15].

---

## 1. Escolha de Paradigma da API

- **A Escolha:** ( X ) Spring MVC com Virtual Threads  |  (  ) Spring WebFlux (Reativo)
- **Justificativa:** 
  A adoção do **Spring MVC em conjunto com Virtual Threads (introduzidas no Java 21)** é a abordagem ideal para este cenário. As Virtual Threads permitem manter o modelo de programação imperativo tradicional (bloqueante por thread de negócio, simples de debugar e de manter), mas eliminam o gargalo clássico do pool de threads do sistema operacional. Com threads virtuais baratas e leves gerenciadas pela JVM, a aplicação consegue escalar facilmente para mais de 10.000 requisições simultâneas bloqueando em chamadas de I/O (como bancos de dados e APIs de pagamento) sem esgotar os recursos de hardware, dispensando a complexidade de código reativo baseada em programação funcional (`Mono`/`Flux`).

---

## 2. Desenho de Resiliência

Para blindar o sistema contra falhas e instabilidades do parceiro de pagamentos externo, adotaremos os seguintes mecanismos do **Resilience4j**:

- **Proteção contra Falhas e Lentidão:**
  - **Circuit Breaker:** Monitora a taxa de falhas nas chamadas ao gateway de pagamento. Se o parceiro cair ou apresentar erros excessivos, o circuito abre (*OPEN*), rejeitando novas chamadas imediatamente com um *fallback* amigável, evitando o esgotamento de threads e o efeito cascata.
  - **TimeLimiter:** Interrompe chamadas ao gateway que ultrapassarem um limite estrito de tempo (ex: 3 segundos), prevenindo que threads fiquem travadas indefinidamente esperando a resposta da rede.
  - **Retry:** Tenta novamente transações que falharam por erros transitórios de rede (ex: falhas rápidas de socket), utilizando backoff exponencial com jitter para evitar sobrecarregar ainda mais o parceiro em recuperação.
  - **Bulkhead:** Isola os recursos alocados para o serviço de pagamento. Se o gateway degradar, o limite de concorrência restringe apenas aquela parte do sistema, garantindo que outras áreas do e-commerce continuem funcionando.
  - **RateLimiter:** Protege tanto a nossa API de abusos de requisições de clientes quanto limita a taxa de envio de requisições disparadas contra o parceiro externo.

- **Proteção contra Duplicidade (Idempotência):**
  - A API de Checkout exigirá o envio obrigatório de um cabeçalho HTTP personalizado: `Idempotency-Key` (UUID gerado pelo cliente).
  - Quando a requisição chega, o microsserviço verifica em um cache distribuído de alta velocidade (ex: Redis) se a chave já foi processada ou está em andamento.
  - Se a chave existir e a transação foi concluída com sucesso, o sistema retorna o resultado anterior armazenado sem reexecutar a cobrança no gateway, garantindo segurança absoluta contra cliques duplos ou reenvios por timeout.

---

## 3. Os Edge Cases (Engenharia do Caos)

1. **E se o cache do Redis cair exatamente no momento em que validamos a `Idempotency-Key` de um pagamento Pix de alto valor?**
   - **Resposta do Sistema:** O sistema implementará um mecanismo de *fallback graceful*. Caso o Redis falhe, a aplicação recorre a uma verificação de unicidade na base de dados relacional com restrição de chave única (*Unique Constraint*), ou rejeita temporariamente a requisição com código HTTP 503 (*Service Unavailable*) e instrução de retry, priorizando a segurança financeira em detrimento da indisponibilidade momentânea.

2. **E se o Circuit Breaker abrir por falha do parceiro, mas os clientes continuarem tentando finalizar a compra em massa?**
   - **Resposta do Sistema:** O método de *fallback* associado ao Circuit Breaker interceptará imediatamente a chamada, respondendo ao cliente com uma mensagem clara de que o serviço de pagamento está instável no momento, oferecendo opcionalmente meios alternativos de pagamento (ex: boleto ou carteira digital interna) ou enfileirando o pedido em estado pendente de reprocessamento assíncrono.

3. **E se ocorrer um timeout na rede logo após o gateway aprovar o pagamento, fazendo com que o nosso cliente receba um erro mas o dinheiro seja debitado?**
   - **Resposta do Sistema:** O mecanismo de *Retry* inteligente validará se a operação é segura para repetir (usando a mesma `Idempotency-Key`). Caso o retry excede o limite, a transação é registrada em estado `PENDENTE_DE_CONCILIACAO` e um job assíncrono de varredura (*Reconciliation Worker*) consulta periodicamente o status da transação diretamente na API do parceiro para efetivar a baixa ou estorno automático.

---

## 4. Testando a Resiliência

- **Estratégia:** 
  Para garantir que o sistema reaja perfeitamente antes de ir para produção, utilizaremos **WireMock** integrado a testes de integração automatizados em ambiente de *Staging*. Vamos simular injeções de falha (*Fault Injection*):
  - Configurar o WireMock para injetar latência de 5 segundos em 50% das requisições de pagamento (testando o *TimeLimiter*).
  - Forçar o retorno de status HTTP 500 contínuo pelo WireMock para comprovar a abertura imediata do *Circuit Breaker* após atingir o limiar de falhas configurado, validando a ativação correta do método de *fallback*.