# System Design: Aula 06 - Observabilidade & Diagnóstico em Produção

> **Contexto do Cenário: O Checkout que Trava sem Dar Erro**
>
> Sua equipe responde pelo serviço de **Checkout** do marketplace, que chama **Pagamento** e **Estoque**. Os três rodam em imagens *Distroless*.
>
> 1. **O Incidente:** Na abertura da Black Friday, com **5.000 TPS**, uma versão nova do Checkout entrou em *Canary*. Minutos depois, os painéis continuavam verdes, com latência média de 120 ms, CPU abaixo de 40% e nenhum pico de HTTP 500.
> 2. **O Impacto:** A conversão despencou. Milhares de reclamações chegaram ao suporte, todas com a mesma cena. O cliente clica em "Finalizar Pedido", a tela fica carregando e o aplicativo desiste por timeout.
> 3. **As Restrições:** A imagem não tem `sh` e não há SSH nos servidores. Cada serviço escreve logs em texto livre, do seu jeito, sem um identificador que ligue a chamada do Checkout à chamada que ele fez ao Pagamento. E dado de cliente que vai parar em log continua sujeito à LGPD.
>
> **Instruções:** Responda às decisões arquiteturais abaixo fundamentando suas escolhas estritamente com base nos requisitos e restrições deste cenário. Documente suas decisões no repositório contínuo do seu projeto através de commits atômicos e bem descritivos.

---

## 1. Logs e Correlação

- **Campos do Log:** Que campos todo log do Checkout precisa ter para você encontrar, no meio de milhares de linhas, as da requisição que travou? Liste os campos e diga para que serve cada um.
  > Todo log deve ser obrigatoriamente padronizado em formato **JSON Estruturado** emitido na `stdout`/`stderr`. Os campos indispensáveis são:
  > - `timestamp`: Data/hora em UTC ISO-8601 de alta precisão (milissegundos/nanossegundos) para ordenação correta na agregação.
  > - `trace_id`: Identificador global da transação distribuída (W3C Trace Context) que conecta a requisição do Checkout às chamadas downstream de Pagamento e Estoque.
  > - `span_id`: Identificador da operação específica em execução dentro do Checkout.
  > - `service_name` & `service_version`: Identifica o microserviço (`checkout-service`) e a versão/build (`v2.1.0-canary`) para isolar se a anomalia ocorre apenas no *Canary*.
  > - `environment`: Identificador do ambiente (`production`).
  > - `log.level`: Nível do log (`INFO`, `WARN`, `ERROR`).
  > - `http.method` & `http.route`: Método e rota do endpoint (`POST /v1/checkout`).
  > - `http.status_code`: Código HTTP de resposta (ex: `200`, `500`, `504`).
  > - `duration_ms`: Tempo total gasto pelo Checkout no processamento da requisição.
  > - `error.type` / `error.message`: Causa raiz do erro ou da desconexão por timeout quando aplicável.
  > - `customer_id_hash`: Hash unidirecional (HMAC-SHA256) do ID do cliente para correlação sem violar a LGPD.

- **O Caminho do Contexto:** Como o mesmo identificador de rastreamento passa do Checkout para o Pagamento e o Estoque numa chamada HTTP?
  > Adotamos o padrão recomendado pela W3C via **OpenTelemetry**:
  > 1. **Propagação de Cabeçalhos (W3C Trace Context):** O Checkout injeta o cabeçalho HTTP `traceparent` nas chamadas de saída para o Pagamento e o Estoque.
  > 2. **Estrutura do `traceparent`:** O cabeçalho segue o formato `00-{trace_id}-{parent_span_id}-{trace_flags}` (ex: `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`).
  > 3. **Consumo Downstream:** O cliente HTTP intercepta a requisição do Checkout, propaga o `trace_id` original via HTTP header, e os serviços de Pagamento e Estoque leem o cabeçalho, registrando suas operações como Spans filhos sob o mesmo `trace_id`.

- **LGPD nos Logs:** Quais dados de cliente ficam fora de logs e traces? Para os identificadores que precisam aparecer, qual tratamento você aplica e quem pode ler esses registros?
  > - **Dados proibidos (Fora dos logs/traces):** Dados Pessoais Sensíveis (DPN) e PII direta em texto claro, tais como: nome completo, CPF, e-mail, número de cartão de crédito, CVV, senhas e endereço residencial.
  > - **Tratamento para identificadores necessários:**
  >   - **Pseudonimização:** O `customer_id` é mascarado utilizando algoritmo **HMAC-SHA256** com uma chave (*pepper*) rotativa armazenada no AWS Secrets Manager / Vault, gerando tokens não reversíveis por terceiros, mas correlacionáveis entre logs.
  >   - **Sanitização de Payloads:** Máscara dinâmica por regex em middlewares para impedir a captura acidental de números de cartão ou tokens nos parâmetros ou corpo da requisição.
  > - **Controle de Acesso (RBAC & Masking):** O acesso à plataforma de agregação de logs (Grafana Loki, Datadog ou OpenSearch) utiliza controle baseado em funções (RBAC). Apenas engenheiros SRE/DevOps em plantão incidente possuem acesso, e a retenção é configurada com expiração de compliance (ex: 30 dias).

---

## 2. Métricas

- **Visão do Cliente (RED):** Quais métricas do Checkout entram no painel? Explique como você mede a duração para que o travamento de uma parte dos clientes não desapareça na média.
  > **Métricas RED monitoradas:**
  > - **Rate (Taxa):** Requisições por segundo por rota e por versão (`http_requests_total{route="/checkout", version="canary"}`).
  > - **Errors (Erros):** Taxa de falhas do ponto de vista do cliente, incluindo cancelamentos de requisição por cliente (*client disconnects*) e HTTP 5xx.
  > - **Duration (Duração):** Latência de processamento das requisições.
  > 
  > **Por que a Média Esconde o Problema e Como Resolver:**
  > A média esconde *outliers* graves (ex: se 95% dos clientes respondem em 50 ms e 5% travam/sofrem timeout de 30s, a média continuará apontando saudáveis ~120 ms).
  > **Solução:**
  > 1. Utilizar **Histogramas de Percentis** (`p90`, `p95`, `p99` e `p99.9`) em vez de médias (`rate(http_request_duration_seconds_sum / http_request_duration_seconds_count)`).
  > 2. Medir a latência do lado do cliente / API Gateway (considerando o timeout da requisição travada) e instrumentar a métrica `http_requests_aborted_total` quando a conexão for encerrada prematuramente antes de enviar a resposta HTTP.

- **Visão dos Recursos (USE):** Que recursos você monitora? Que sinal acusaria saturação antes do primeiro HTTP 500?
  > **Métricas USE monitoradas:**
  > - **Utilization (Utilização):** Uso de CPU, memória heap do container, uso de portas ephemeral.
  > - **Saturation (Saturação):** Fila de requisições do servidor web, threads/tasks pendentes no executor do runtime.
  > - **Errors (Erros):** Erros de alocação de recursos, rejeições em pools.
  > 
  > **Sinal de Saturação que Acusa o Problema sem Dar HTTP 500:**
  > **Saturação do Pool de Conexões HTTP/Database (`connection_pool_waiting_threads` / `db_pool_active_connections` em 100%)** e **Tamanho da Fila de Threads da Aplicação (`thread_pool_queue_size`)**. Quando as conexões do pool para o serviço de Pagamento esgotam por starvation, as novas threads do Checkout ficam em estado `WAITING` sem estourar tempo limite imediatamente, mantendo o uso de CPU baixo (<40%), sem emitir 500 imediato, porém segurando o cliente até estourar o timeout da camada superior.

- **Labels:** Quais labels você permite nessas métricas e quais proíbe? Diga o efeito de cada escolha no servidor de métricas.
  > - **Labels Permitidos (Baixa Cardinalidade):** `environment="prod"`, `version="v2.1.0-canary"`, `http_status="200"`, `http_method="POST"`, `route="/v1/checkout"`.
  >   - *Efeito:* Permite agregação rápida, gráficos em tempo real e baixo consumo de memória no Prometheus/Thanos.
  > - **Labels Proibidos (Alta/Infinita Cardinalidade):** `user_id`, `customer_id`, `order_id`, `ip_address`, `cart_id`.
  >   - *Efeito:* A explosão de cardinalidade cria milhões de séries temporais únicas. Isso esgota a RAM do TSDB (Prometheus), torna os painéis extremamente lentos ou indisponíveis por estouro de memória (OOM), colapsando o sistema de métricas no pico da Black Friday.

---

## 3. Traces e Amostragem

- **A Escolha:** ( ) Head Sampling  ( ) Tail Sampling  ( X ) Combinação das duas

- **Justificativa:** Guardar 100% dos traces a 5.000 TPS custa caro. Qual é a sua regra de retenção, e como ela evita que justamente as requisições que travaram sejam descartadas?
  > **Estratégia Combinada para Custo e Visibilidade:**
  > 1. **Head Sampling no Agente/App (1% a 5%):** Captura estatística padrão na origem para manter o overhead de CPU/Rede baixo a 5.000 TPS.
  > 2. **Tail Sampling no OpenTelemetry Collector (Decisão Tardia):** O Collector segura os spans na memória antes de tomar a decisão final de descarte ou persistência.
  > 
  > **Regra de Retenção Dinâmica no Tail Sampling:**
  > - **Descarte de Sucesso:** Guarda apenas 0.5% das transações HTTP 200 que finalizam em latência normal (< 200 ms).
  > - **Retenção de 100% (Anomalias e Erros):**
  >   - Retém **100%** de traces que resultem em HTTP Status 5xx, 4xx relevantes ou com a tag `error=true`.
  >   - Retém **100%** de traces cuja duração total exceda a faixa do p95 (ex: `duration > 2000ms`).
  >   - Retém **100%** de traces que apresentem desconexão abrupta do cliente ou span de downstream não finalizado (*timeout*).

---

## 4. Estratégia de Testes

- **Qual a nossa estratégia para testar isso e garantir que funciona?** Antes da próxima Black Friday, como você prova que os painéis e os alertas mostrariam este incidente?
  > **Estratégia de Validação Contínua & Teste de Carga Observável:**
  > 1. **Testes de Estresse com Injeção de Latência (k6 / Chaos Mesh):**
  >    - Utilizar scripts **k6** simulando 5.000 TPS contra o ambiente de staging/pre-prod.
  >    - Injetar gargalo intencional no mock do microserviço de Pagamento (ex: simular latência de 15 segundos ou *socket hang-up* em 10% do tráfego do *Canary*).
  > 2. **Validação da Prova da Telemetria (GameDay / Chaos Engineering):**
  >    - Executar o teste de estresse e verificar se o percentil `p99` de latência dispara o alerta no Slack/PagerDuty em menos de 2 minutos.
  >    - Confirmar que a métrica de *Client Disconnects / HTTP Aborts* subiu e que o Tail Sampler reteve 100% dos traces lentos do *Canary*.
  >    - Garantir que o painel exiba claramente a divergência de performance entre as versões `v2.0.0-stable` e `v2.1.0-canary` sem travar o Prometheus.