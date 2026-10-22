# 🚀 Desafio de Observabilidade & Teste de Carga - Checkout Service

Este projeto consiste numa demonstração prática de observabilidade e engenharia de resiliência aplicada a um microsserviço de **Checkout** desenvolvido em **Spring Boot 3.2.5**. 

O objetivo principal do desafio foi integrar telemetria distribuída (**OpenTelemetry**), monitorização de métricas (**Prometheus**) e rastreamento de chamadas (**Jaeger**), validando a resiliência da aplicação sob picos extremos de tráfego simulados via **k6**.

---

## 🏗️ Arquitetura da Solução

A arquitetura foi orquestrada em ambiente Docker, permitindo a comunicação entre a aplicação, os coletores de telemetria e o gerador de tráfego.

```
                  +-------------------+
                  |  k6 Load Tester   |
                  |  (Docker/k6)      |
                  +---------+---------+
                            |
                            | (HTTP POST /api/checkout)
                            v
+-----------------------------------------------------------------+
| Docker Network / Host Bridge (host.docker.internal)             |
|                                                                 |
|   +-----------------------+                                     |
|   |   Checkout Service    |<----------- (Scrape /actuator)      |
|   |  (Spring Boot 3.2.5)  |                   |                 |
|   +-----------+-----------+                   |                 |
|               |                               |                 |
|               | (OTLP Traces)                 v                 |
|               v                     +-------------------+       |
|     +-------------------+           |    Prometheus     |       |
|     |   Jaeger Tracing  |           |     (Metrics)     |       |
|     +-------------------+           +-------------------+       |
+-----------------------------------------------------------------+
```

### Componentes:
* **Checkout Service:** Application Spring Boot 3.2.5 compilada via Docker Multi-stage build com suporte a micrométricas Actuator e OpenTelemetry Java Agent.
* **Prometheus:** Servidor de monitorização responsável por realizar o *scrape* contínuo do endpoint `/actuator/prometheus`.
* **Jaeger:** Plataforma de rastreamento distribuído que recebe *spans* e *traces* OTLP exportados pelo Java Agent do OpenTelemetry.
* **k6:** Ferramenta de teste de carga para simulação de cenários de *spike testing* (picos súbitos de acessos).

---

## 🛠️ Tecnologias Utilizadas

* **Linguagem & Framework:** Java 17, Spring Boot 3.2.5, Spring Web, Spring Boot Actuator
* **Observabilidade:** Micrometer Prometheus, OpenTelemetry Java Agent (v2.2.0), Jaeger All-in-One
* **Infraestrutura & Containerização:** Docker, Docker Compose, Docker Multi-Stage Builds
* **Testes de Carga:** Grafana k6
* **Ambiente de Execução:** Windows Command Prompt (CMD)

---

## 🚀 Passo a Passo para Execução

### Pré-requisitos
* **Docker Desktop** instalado e ativo.
* **Git** instalado (opcional).

---

### 1. Clonar o Repositório e Navegar até à Pasta do Projeto
Abre o **Prompt de Comando (CMD)** e navega até à pasta do laboratório:

```cmd
cd /d "E:\AulaJava-Ada\Aula 6\desafio\lab"
```

---

### 2. Construir e Subir a Infraestrutura (Docker Compose)
Executa a compilação da imagem Docker da aplicação e sobe todos os serviços em segundo plano:

```cmd
docker compose build --no-cache
docker compose up -d
```

> **Serviços Ativos:**
> * **Checkout Service:** `http://localhost:8080`
> * **Prometheus:** `http://localhost:9090`
> * **Jaeger UI:** `http://localhost:16686`

---

### 3. Validar a Aplicação com um Teste de Requisição Única
Certifica-te de que o endpoint de checkout está ativo executando o seguinte `curl` no CMD:

```cmd
curl -X POST http://localhost:8080/api/checkout ^
  -H "Content-Type: application/json" ^
  -d "{\"cart_id\": \"cart-999\", \"payment_method\": \"credit_card\"}"
```

**Resposta Esperada:**
```json
{"message":"Checkout realizado com sucesso","status":"SUCCESS","order_id":"..."}
```

---

### 4. Executar o Teste de Carga (Spike Test com k6)
Executa o container do k6 montando o diretório local e mapeando a rede host para permitir comunicação cruzada entre containers:

```cmd
docker run --rm -i --add-host=host.docker.internal:host-gateway -v "%cd%:/app" -w /app grafana/k6 run k6-spike-test.js
```

O teste simulará uma rampa de utilizadores virtuais (VUs) subindo até **1.000 VUs simultâneas** num cenário de pico.

---

## 📊 Resultados Obtidos

1. **Testes de Resiliência e Carga:**
   * A aplicação Spring Boot suportou o pico simulado pelo k6 sem quedas de serviço, registando processamento massivo no endpoint `/api/checkout`.

2. **Métricas de Performance (Prometheus):**
   * Coleta com sucesso da métrica `http_server_requests_seconds_count` agrupada por métodos HTTP (`POST` e `GET`), status (`200 OK`) e cenários de ambiente (`production`).
   * Rastreio contínuo do estado da JVM e tempo de resposta das requisições via Spring Actuator.

3. **Rastreamento Distribuído (Jaeger):**
   * Captura automática de *spans* sem necessidade de alteração no código de negócio graças ao suporte do OpenTelemetry Java Agent.
   * Rastreio e visibilidade das rotas `/api/checkout` e chamadas internas do ecossistema.

---

## 📁 Estrutura do Projeto

```text
lab/
├── src/                    # Código fonte Java (Spring Boot)
│   └── main/java/com/
│       └── example/demo/
│           ├── CheckoutApplication.java
│           └── CheckoutController.java
├── Dockerfile              # Build multi-stage da aplicação
├── docker-compose.yml      # Orquestração dos containers (App, Prometheus, Jaeger)
├── prometheus.yml          # Configuração de scrape do Prometheus
├── k6-spike-test.js        # Script de teste de carga (Spike Testing)
└── pom.xml                 # Dependências Maven
```