# 🔬 Pesquisa Individual: APIs REST Resilientes & Arquitetura

> **Aula 02 - Java PE-JV-003 | Ada Tech**
> **Tema:** Análise de Paradigmas de Concorrência, Mecanismos de Resiliência e Estratégia de Testes contra Falhas Externas.

---

## 1. Spring MVC vs. Spring WebFlux (O Impacto das Virtual Threads)

### 📊 Comparativo Técnico

| Critério | Spring MVC (Tradicional) | Spring WebFlux (Reativo) | Spring MVC + Virtual Threads (Java 21) |
| :--- | :--- | :--- | :--- |
| **Modelo de Execução** | Thread-per-request (Thread do S.O.) | Event Loop Assíncrono / Não-bloqueante | Thread Virtual (Project Loom / Carrier Thread) |
| **Estilo de Código** | Imperativo (Simples, sequencial) | Funcional / Reativo (`Mono`, `Flux`) | Imperativo (Simples, sequencial) |
| **Custo de Bloqueio de I/O** | Alto (Trava thread do S.O. em E/S) | Baixo (Desaloca a thread no I/O) | Quase Nulo (A JVM desmonta a Virtual Thread) |
| **Curva de Aprendizado** | Baixa | Alta | Baixa |
| **Suporte a 10k Reqs Simultâneas** | Exige pools massivos (Gargalo de Hardware) | Suporta nativamente | Suporta nativamente com baixo overhead |

### 💡 Análise do Impacto das Virtual Threads
As **Virtual Threads (Project Loom, Java 21)** revolucionam a escolha de arquitetura ao trazer o melhor dos dois mundos:
1. **Simplicidade do Modelo Imperativo:** Mantém-se o padrão tradicional de programação, facilitando leitura de código, depuração (*stack traces*) e manutenção.
2. **Alta Escalabilidade de I/O:** Quando uma Virtual Thread bloqueia esperando a resposta da API externa de pagamento ou do banco de dados, a JVM a desassocia (*unmount*) da thread do sistema operacional (*carrier thread*). Essa thread do S.O. fica livre para processar outras requisições.
3. **Conclusão:** Para a API de Checkout com **10.000 usuários simultâneos**, o **Spring MVC com Virtual Threads** elimina a necessidade de migration ou adoção do Spring WebFlux, oferecendo a escalabilidade necessária com menor complexidade de desenvolvimento.

---

## 2. Mecanismos de Resiliência (Resilience4j) & Idempotência

### 🛡️ Padrões do Resilience4j

#### A. TimeLimiter
* **Funcionamento:** Define um limite máximo de tempo de espera para a execução de uma chamada (ex: 3000ms para a API de pagamento).
* **Finalidade:** Se a API externa não responder dentro do tempo limite, a operação é interrompida imediatamente, lançando um `TimeoutException`. Isso evita que a requisição fique travada indefinidamente aguardando a rede.

#### B. Bulkhead
* **Funcionamento:** Limita o número máximo de chamadas simultâneas que podem acessar uma determinada dependência externa (ex: máximo de 100 conexões concorrentes para o gateway de pagamento).
* **Finalidade:** Isola falhas. Se o parceiro de pagamentos degradar e ficar lento, o Bulkhead restringe o consumo de recursos apenas àquela dependência, impedindo que a degradação afete outras partes do sistema (como busca de produtos ou autenticação).

#### C. RateLimiter
* **Funcionamento:** Controla o número de requisições permitidas dentro de uma janela de tempo específica (ex: máximo de 500 chamadas por segundo).
* **Finalidade:** Protege o sistema contra picos indevidos de tráfego (DDoS ou bots) e garante o cumprimento de limites operacionais (*SLA*) impostos pelos parceiros de pagamento.

---

### 🔑 Funcionamento da `Idempotency-Key` num `POST`

Como requisições HTTP do tipo `POST` não são inerentemente idempotentes (reenviar o payload pode criar múltiplos registros/cobranças), a `Idempotency-Key` é utilizada para garantir **segurança transacional**:

1. **Geração pelo Cliente:** O cliente (frontend ou aplicativo) gera um identificador único (geralmente um UUIDv4) antes de enviar a requisição de checkout e envia esse valor no cabeçalho HTTP `Idempotency-Key`.
2. **Checagem de Existência:** A API de Checkout intercepta o cabeçalho e verifica em um cache distribuído de alta velocidade (ex: Redis) se a chave já existe:
   * **Se a chave é nova:** A API registra a chave no Redis com estado `IN_PROGRESS` e processa a cobrança.
   * **Se a chave já existe e está em `COMPLETED`:** A API interrompe o processamento imediatamente e retorna a resposta anterior salva no cache, sem chamar a API de pagamento parceira novamente.
   * **Se a chave está em `IN_PROGRESS`:** A API retorna status HTTP `409 Conflict` ou `429 Too Many Requests`, informando que a operação anterior ainda está em andamento.
3. **Ciclo de Vida (TTL):** As chaves de idempotência mantêm um tempo de vida (*Time-To-Live - TTL*) no cache (geralmente de 24 a 48 horas), garantindo retenção suficiente para a janela de retentativas do cliente e evitando o crescimento ilimitado da memória.

---

## 3. Estratégias de Teste de Resiliência (WireMock & Fault Injection)

Para homologar a arquitetura antes do pico de tráfego, simulamos cenários de estresse e queda de dependências em ambiente de testes:

### 🧪 Testes com WireMock e Injeção de Falhas

1. **Simulação de Latência Extrema (Testando TimeLimiter):**
   * Configura-se o WireMock para responder à chamada de pagamento com um atraso proposital de 10 segundos.
   * **Comportamento Esperado:** O `TimeLimiter` deve interromper a chamada após 3 segundos, liberando a thread e acionando o método de *fallback*.

2. **Simulação de Erros em Cascata (Testando Circuit Breaker):**
   * Configura-se o WireMock para retornar status HTTP `500 Internal Server Error` ou `503 Service Unavailable` em 100% das chamadas durante 1 minuto.
   * **Comportamento Esperado:** Após atingir a taxa de falhas configurada (ex: 50% das últimas 10 chamadas), o *Circuit Breaker* deve transicionar para o estado `OPEN`. Todas as chamadas subsequentes devem ser rejeitadas instantaneamente sem passar pelo WireMock, retornando a resposta de *fallback* configurada.

3. **Simulação de Recuperação (Estado Half-Open):**
   * Restabelece-se o funcionamento correto no WireMock após o *Circuit Breaker* abrir.
   * **Comportamento Esperado:** Transcorrido o tempo de espera (*waitDurationInOpenState*), o *Circuit Breaker* transiciona para `HALF_OPEN`, permitindo um número limitado de chamadas de teste. Caso essas chamadas tenham sucesso, o circuito fecha (`CLOSED`), restaurando o fluxo normal do sistema.