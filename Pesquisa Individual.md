# 📚 Pesquisa Individual: Concorrência, Resiliência e Testes de Carga

Respostas técnicas detalhadas para os conceitos de arquitetura, mecanismos de resiliência e validação do sistema[cite: 8].

---

## 1. Spring MVC vs. Spring WebFlux e o Impacto das Virtual Threads

| Característica | Spring MVC | Spring WebFlux |
| :--- | :--- | :--- |
| **Modelo de I/O** | Bloqueante (*Thread-per-request*) | Não-bloqueante (*Event-loop*) |
| **Servidor Embutido** | Tomcat / Jetty | Netty |
| **Consumo de Memória/Threads** | Alto em grande volume de requisições bloqueadas em I/O | Muito baixo, poucas threads gerenciam milhares de conexões |
| **Paradigma** | Imperativo (código sequencial simples) | Reativo (Streams, Mono/Flux) |

### 💡 O impacto das Virtual Threads (Java 21+ / Project Loom)[cite: 8]
* **Mudança de Paradigma:** As Virtual Threads resolvem o problema de esgotamento de *threads* do sistema operacional sem exigir a complexidade do código reativo.
* **Spring MVC + Virtual Threads:** Permite manter a escrita do código imperativo tradicional e ainda assim suportar milhares de conexões simultâneas de I/O bloqueante com altíssimo desempenho.
* **Quando ainda usar WebFlux?** WebFlux permanece ideal para cenários com streaming continuo de dados (WebSockets, Server-Sent Events, RSocket) e gerenciamento nativo de *backpressure*.

---

## 2. Mecanismos de Resiliência (Resilience4j) & Idempotência

### 🛡️ Padrões de Resiliência[cite: 8]:
* **TimeLimiter:** Define um tempo limite estrito para o término de uma operação assíncrona. Se o serviço ultrapassar esse tempo, lança uma exceção de *Timeout* e cancela a execução, liberando os recursos[cite: 8].
* **Bulkhead:** Isola os recursos (como *pools* de threads ou conexões) dedicados a uma dependência específica. Impede que a lentidão em um serviço externo consuma todos os recursos da aplicação inteira[cite: 8].
* **RateLimiter:** Limita a taxa máxima de requisições em um intervalo de tempo determinado (ex.: no máximo 100 req/s). Protege nossa API de sobrecarga e previne o estouro de limites contratados com APIs parceiras[cite: 8].

### 🔄 Como funciona a `Idempotency-Key` em requisições `POST`?[cite: 8]
1. **Envio da Chave:** O cliente envia um UUID único no cabeçalho HTTP (ex.: `Idempotency-Key: 7b3a9e10-...`) no momento da requisição[cite: 8].
2. **Verificação Inicial (Redis):** A API busca a chave em um cache distribuído.
3. **Tomada de Decisão:**
   * **Se a chave estiver em processamento (`PROCESSING`):** Retorna `409 Conflict` ou `429 Too Many Requests` para bloquear requisições simultâneas idênticas[cite: 8].
   * **Se o processamento foi concluído (`COMPLETED`):** Retorna o resultado salvo no cache sem reprocessar a cobrança ou a lógica de negócio[cite: 8].
   * **Se a chave for inédita:** Salva a chave com estado `PROCESSING`, executa a operação, salva a resposta e altera o estado para `COMPLETED`.

---

## 3. Testes de API e Simulação de Falhas Externas (WireMock & Fault Injection)

Para garantir que o nosso sistema permaneça estável mesmo quando as APIs parceiras caírem ou apresentarem lentidão[cite: 8]:

* **WireMock:**
  * **Simulação de Falhas:** Permite criar *mocks* HTTP para retornar erros `500 Internal Server Error`, `503 Service Unavailable` ou payloads corrompidos para validar se o **Retry** e o **Circuit Breaker** respondem corretamente[cite: 8].
  * **Injeção de Latência:** Utilização de `withFixedDelay` ou `withLogNormalRandomDelay` para simular requisições lentas e testar o funcionamento do **TimeLimiter**[cite: 8].
* **Injeção de Falhas (Fault Injection & Chaos Engineering)[cite: 8]:**
  * **Chaos Mesh / Toxiproxy:** Ferramentas para injetar falhas no nível de rede (perda de pacotes, alta latência artificial, queda abrupta de conexões TCP) nos ambientes de staging/testes integrados.
  * **Validação:** Assegura que os cenários de *Fallback* funcionam perfeitamente sem derrubar a API principal nem esgotar a memória do servidor[cite: 8].