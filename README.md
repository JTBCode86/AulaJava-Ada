# ☕ Curso de Java & System Design - Ada Tech

Este repositório destina-se a armazenar os códigos, desafios de engenharia, decisões arquiteturais e projetos práticos desenvolvidos durante a especialização em **Java e Arquitetura de Sistemas** ministrada pela **Ada Tech**.

---

## 📌 Sobre o Repositório

O objetivo deste projeto vai além da sintaxe da linguagem: cada aula simula um **cenário real de engenharia de software corporativo** (como picos de tráfego de Black Friday, concorrência extrema em e-commerce, consistência eventual e microsserviços autônomos), focando em código limpo, performance, resiliência e padrões de mercado.

---

## 📂 Trilha de Aulas e Desafios de Engenharia

O repositório está organizado modularmente por aulas, contendo os desafios práticos correspondentes:

| Pasta / Aula | Tema Principal & Contexto de Negócio | Desafio Técnico / Arquitetural |
| :--- | :--- | :--- |
| **`Aula 2/`** | Banco de Dados, Índices & Concorrência | Otimização de consultas lentas (`EXPLAIN`), mitigação de *page splits* com UUIDv7, uso de Locks Pessimistas sob alta disputa de estoque e estratégias de persistência poliglota. |
| **`Aula 3/`** | Arquitetura Orientada a Eventos & Consistência | Resolução do problema de *Dual Write* utilizando o padrão **Transactional Outbox**, CDC com Debezium e mensageria assíncrona. |
| **`Aula 4/`** | Transações Distribuídas & Resiliência | Orquestração de Sagas, compensações automáticas sem rollback físico, CQRS para desacoplamento de leitura/escrita e estratégias de testes de falha (Chaos Engineering). |
| **`Aula 5/`** | Guia Prático de Engenharia de Release | Conteinerização OCI, otimização de imagens Java com Paketo Buildpacks e Distroless, estratégias de CI/CD, garanta de *Reproducible Builds* e deploys *zero-downtime*. |
| **`Aula 6/`** | Observabilidade & Diagnóstico em Produção | Diagnóstico de gargalos distribuídos sem acesso a shell em imagens *Distroless*, correlação de logs com W3C `traceparent`, amostragem de traces (*Head* vs *Tail Sampling*), métricas RED/USE, prevenção de explosão de cardinalidade e conformidade com a LGPD. |
| **`ProjetoFinal/`** | **MeliCore Checkout Engine (Projeto Integrador)** | Arquitetura de microsserviços de alto desempenho, resiliente e orientada a eventos. Implementação do **Padrão SAGA** via **Temporal.io**, **Transactional Outbox Pattern** com PostgreSQL e mensageria confiável via **Apache Kafka** com suporte a Dead Letter Queue (**DLQ**). |
| *Próximas Aulas* | *A definir / Em desenvolvimento* | *Novos desafios de alta escala e resiliência.* |

---

## 🛠️ Tecnologias e Ferramentas

- **Linguagem:** Java (JDK 17+)
- **Frameworks:** Spring Boot, Spring Data JPA / Hibernate
- **Arquitetura & Padrões:** Sagas (Orquestração), Transactional Outbox, CQRS, Domain-Driven Design (DDD)
- **Mensageria & Streaming:** Apache Kafka, Debezium (CDC)
- **Bancos de Dados:** PostgreSQL, Redis
- **Containerização & DevOps:** Docker, Docker Compose, Paketo Buildpacks, Images Distroless (OCI)
- **Controle de Versão:** Git & GitHub

---

## 🗂️ Estrutura do Projeto

```text
.
├── Aula 2/              # Otimização de bancos, índices, UUIDv7 e Locks
├── Aula 3/              # Transactional Outbox Pattern e integração com Kafka
├── Aula 4/              # Transações distribuídas, Sagas e CQRS
├── Aula 5/              # Guia Prático de Engenharia de Release e Imagens OCI
├── Aula 6/	   	 # Observabilidade, Diagnóstico em Produção e Telemetria Distribuída	
├── ProjetoFinal/        # MeliCore Checkout Engine (Spring Boot + Temporal + Postgres + Kafka)
├── .gitignore           # Arquivos e pastas ignorados pelo Git
└── README.md            # Documentação do repositório
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