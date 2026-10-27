# ☕ Curso de Java & System Design - Ada Tech

Este repositório destina-se a armazenar os códigos, desafios de engenharia, decisões arquiteturais e projetos práticos desenvolvidos durante a especialização em **Java e Arquitetura de Sistemas** ministrada pela **Ada Tech**.

<p align="center">
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
  <img src="https://img.shields.io/badge/Debezium-FF6B6D?style=for-the-badge&logo=debezium&logoColor=white" alt="Debezium">
  <img src="https://img.shields.io/badge/Temporal-00C7B7?style=for-the-badge&logo=temporal&logoColor=white" alt="Temporal">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white" alt="Kubernetes">
</p>

---

## 📌 Sumário
- [Sobre o Repositório](#-sobre-o-repositório)
- [Entregáveis & Templates de System Design](#-entregáveis--templates-de-system-design)
- [Trilha de Aulas e Desafios de Engenharia](#-trilha-de-aulas-e-desafios-de-engenharia)
- [Tecnologias e Ferramentas](#️-tecnologias-e-ferramentas)
- [Estrutura das Pastas](#️-estrutura-das-pastas)
- [Como Executar os Projetos](#-como-executar-os-projetos)

---

## 📌 Sobre o Repositório

O objetivo deste projeto vai além da sintaxe da linguagem: cada aula simula um **cenário real de engenharia de software corporativo** (como picos de tráfego de Black Friday, concorrência extrema em e-commerce, consistência eventual e microsserviços autônomos), focando em código limpo, performance, resiliência e padrões de mercado.

---

## 📋 Entregáveis & Templates de System Design

Abaixo estão listados os templates de System Design e entregáveis de engenharia referentes a cada módulo do curso:

| Aula | Tema | Arquivo de Entregável / Template |
| :---: | :--- | :--- |
| **02** | APIs Resilientes | [`PE-JV-003-Aula-02-Template-System-Design.md`](./Aula02/PE-JV-003-Aula-02-Template-System-Design.md) |
| **03** | Banco de Dados em Larga Escala | [`PE-JV-003-Aula-03-Template-System-Design.md`](./Aula03/PE-JV-003-Aula-03-Template-System-Design.md) |
| **04** | Microsserviços & Padrão Saga | [`PE-JV-003-Aula-04-Template-System-Design.md`](./Aula04/PE-JV-003-Aula-04-Template-System-Design.md) |
| **05** | Containers, CI/CD & Deploy sem Downtime | [`PE-JV-003-Aula-05-Template-System-Design.md`](./Aula05/PE-JV-003-Aula-05-Template-System-Design.md) |
| **06** | Observabilidade & Diagnóstico em Produção | [`PE-JV-003-Aula-06-Template-System-Design.md`](./Aula06/PE-JV-003-Aula-06-Template-System-Design.md) |

---

## 📂 Trilha de Aulas e Desafios de Engenharia

O repositório está organizado modularmente por aulas, contendo os desafios práticos correspondentes:

| Pasta / Aula | Tema Principal & Contexto de Negócio | Desafio Técnico / Arquitetural |
| :--- | :--- | :--- |
| [**`Aula02/`**](./Aula02/) | APIs Resilientes & Integração de Checkout | Implementação de resiliência com Resilience4j (Circuit Breaker, TimeLimiter, Bulkhead, RateLimiter) e garantia de idempotência com `Idempotency-Key`[cite: 61]. |
| [**`Aula03/`**](./Aula03/) | Banco de Dados em Larga Escala & Concorrência | Otimização de consultas lentas (`EXPLAIN`), mitigação de *page splits* com UUIDv7, uso de Locks Otimistas e Pessimistas sob alta disputa de estoque e persistência poliglota[cite: 62]. |
| [**`Aula04/`**](./Aula04/) | Microsserviços, Sagas & Consistência Distribuída | Orquestração de Sagas, transações compensatórias sem rollback físico, resolução de *Dual Write* via Transactional Outbox (Debezium/CDC) e CQRS[cite: 63]. |
| [**`Aula05/`**](./Aula05/) | Containers, CI/CD & Deploy sem Downtime | Empacotamento OCI com imagens *Distroless*, esteira imutável, migração de banco sem downtime usando o padrão *Expand-Contract* e *Canary Release*[cite: 64]. |
| [**`Aula06/`**](./Aula06/) | Observabilidade & Diagnóstico em Produção | Rastreabilidade distribuída com OpenTelemetry e W3C `traceparent`, amostragem (*Head* vs *Tail Sampling*), métricas RED/USE e pseudonimização alinhada à LGPD[cite: 65]. |
| [**`ProjetoBonus/`**](./ProjetoBonus/) | **MeliCore Checkout Engine (Projeto Integrador)** | Arquitetura de microsserviços de alto desempenho, resiliente e orientada a eventos. Implementação do **Padrão SAGA** via **Temporal.io**, **Transactional Outbox Pattern** com PostgreSQL e mensageria confiável via **Apache Kafka** com suporte a Dead Letter Queue (**DLQ**). |

---

## 🛠️ Tecnologias e Ferramentas

- **Linguagem:** Java (JDK 17+)
- **Frameworks:** Spring Boot, Spring Data JPA / Hibernate
- **Arquitetura & Padrões:** Sagas (Orquestração), Transactional Outbox, CQRS, Domain-Driven Design (DDD)
- **Mensageria & Streaming:** Apache Kafka, Debezium (CDC)
- **Bancos de Dados:** PostgreSQL, Redis
- **Containerização & DevOps:** Docker, Docker Compose, Paketo Buildpacks, Imagens Distroless (OCI)
- **Controle de Versão:** Git & GitHub

---

## 🗂️ Estrutura das Pastas

```text
AulaJava-Ada/
├── Aula02/                  # Otimização de bancos, índices, UUIDv7, Locks e System Design 02
│   └── desafio/             # Desafios práticos da Aula 02: Checkout Resilience Demo
├── Aula03/                  # Transactional Outbox Pattern, Kafka e System Design 03
│   └── desafio/             # Desafios práticos da Aula 03: Benchmark de Identificadores PostgreSQL
├── Aula04/                  # Transações distribuídas, Sagas, CQRS e System Design 04
│   └── desafio/             # Desafios práticos da Aula 04: Arquitetura Event-Driven
├── Aula05/                  # Engenharia de Release, Imagens OCI e System Design 05
│   └── desafio/             # Desafios práticos da Aula 05: Serviço de Checkout
├── Aula06/                  # Observabilidade, Telemetria Distribuída e System Design 06
│   └── desafio/             # Desafios práticos da Aula 06: Observabilidade & Teste de Carga
├── ProjetoBonus/            # MeliCore Checkout Engine (Spring Boot + Temporal + Postgres + Kafka)
├── .gitignore               # Arquivos e pastas ignorados pelo Git
└── README.md                # Documentação principal do repositório
```

---

## 🚀 Como Executar os Projetos

1. Clone o repositório:

```bash
git clone https://github.com/JTBCode86/AulaJava-Ada.git
```

2. Acesse a pasta da aula desejada:

```bash
cd AulaJava-Ada/"Aula x"
```

3. Suba as dependências de infraestrutura (quando aplicável via Docker Compose):

```bash
docker compose up -d
```

4. Abra na sua IDE de preferência (IntelliJ IDEA) e execute os testes ou a aplicação principal.

---

👤 **Autor**  
Desenvolvido por Jean Barbosa ([@JTBCode86](https://github.com/JTBCode86)).