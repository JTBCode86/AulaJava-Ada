# Benchmark de Identificadores PostgreSQL — BIGINT Sequencial vs UUIDv4

Este repositório contém o desafio prático de **System Design (Aula 3)** focado na comparação de performance, utilização de espaço em disco e comportamento de índices entre **Chaves Primárias Sequenciais (BIGINT)** e **Chaves Primárias Aleatórias (UUIDv4)** no PostgreSQL.

---

## 📌 Contexto e Objetivos

Ao projetar sistemas de alta escala (OLTP), a escolha do tipo de chave primária afeta diretamente a eficiência das gravações no banco de dados, o tamanho dos índices e o uso do *Buffer Pool* na RAM.

* **BIGINT (IDENTITY)**: Estrutura ordenada e monotônica. Os dados são inseridos no modo *append-only*, resultando em alta localidade de referência e baixíssima fragmentação nas páginas da árvore B-Tree.
* **UUIDv4 (Random UUID)**: Gera identificadores aleatórios de 128 bits. Proporciona unicidade global sem dependência de uma sequência central, mas causa fragmentação constante da árvore B-Tree (*page splits*) e maior *overhead* de memória e armazenamento.

---

## 🛠️ Tech Stack & Infraestrutura

* **Linguagem**: Java 21
* **Framework**: Spring Boot 3.2.3 (Spring Data JPA / Hibernate)
* **Banco de Dados**: PostgreSQL 16
* **Gerenciador de Dependências**: Apache Maven
* **Orquestração**: Docker & Docker Compose

---

## 🚀 Como Executar o Projeto

Todo o ambiente de banco de dados e aplicação Spring Boot foi automatizado através do Docker.

### Pré-requisitos
* [Docker](https://www.docker.com/) e Docker Compose instalados.

### Passos para Execução

1. Clone este repositório:
   ```bash
   git clone <URL_DO_REPOSITORIO>
   cd pk-benchmark-demo
   ```

2. Suba o ambiente automatizado com o Docker Compose:
   ```bash
   docker compose up --build
   ```

3. O container `postgres-benchmark` iniciará e executará os scripts de inicialização SQL em `init/01_DDL_DML.sql`. Em seguida, o container `benchmark-app` subirá a aplicação Spring Boot, executando o benchmark automático de **50.000 inserções em lote** para cada tipo de chave.

---

## 📊 Resultados do Benchmark & Execution Plan

Abaixo estão descritos os dados extraídos das consultas de diagnóstico e dos planos de execução (`EXPLAIN ANALYZE`).

### 1. Tamanho da Tabela e dos Índices (`pg_relation_size`)

| Tabela | ID Type | Registros | Tamanho Total | Tamanho Apenas dos Índices |
| :--- | :--- | :--- | :--- | :--- |
| `orders_sequential` | `BIGINT` | 100.000 | **8984 kB (~8.7 MB)** | **2288 kB (~2.2 MB)** |
| `orders_uuid` | `UUIDv4` | 100.000 | **12 MB** | **4288 kB (~4.2 MB)** |

> **Análise**: O uso de `UUIDv4` aumentou o tamanho do índice em **quase 90%** (de 2.2 MB para 4.2 MB). Em bases de dados com centenas de milhões de linhas, esse *overhead* reduz a quantidade de páginas do índice mantidas em memória cache (*Buffer Pool*), gerando mais operações de I/O em disco.

---

### 2. Análise do Inserção (`INSERT INTO ... SELECT`)

#### Inserção Sequencial (`orders_sequential`)
```text
Insert on orders_sequential  (cost=0.00..3750.00 rows=0 width=0) (actual time=438.415..438.415 rows=0 loops=1)
  ->  Subquery Scan on "*SELECT*"  (cost=0.00..3750.00 rows=100000 width=250) (actual time=15.618..160.686 rows=100000 loops=1)
        ->  Function Scan on generate_series g  (cost=0.00..2750.00 rows=100000 width=48) (actual time=15.026..100.638 rows=100000 loops=1)
Planning Time: 0.732 ms
Execution Time: 439.469 ms
```

#### Inserção Aleatória (`orders_uuid`)
```text
Insert on orders_uuid  (cost=0.00..3750.00 rows=0 width=0) (actual time=634.653..634.654 rows=0 loops=1)
  ->  Subquery Scan on "*SELECT*"  (cost=0.00..3750.00 rows=100000 width=258) (actual time=13.652..243.802 rows=100000 loops=1)
        ->  Function Scan on generate_series g  (cost=0.00..2750.00 rows=100000 width=48) (actual time=13.622..122.164 rows=100000 loops=1)
Planning Time: 0.099 ms
Execution Time: 635.348 ms
```

> **Análise**: A inserção de 100.000 linhas via consulta direta mostrou um tempo de execução de **439 ms** para `BIGINT` contra **635 ms** para `UUIDv4` — um aumento de aproximadamente **44% no tempo de escrita**, decorrente da necessidade de reorganização das páginas de índice B-Tree aleatórias (*page splits*).

---

### 3. Leitura Pontual por Chave Primária (`SELECT BY ID`)

#### Consulta Sequencial por `BIGINT`
```text
Index Scan using orders_sequential_pkey on orders_sequential  (cost=0.29..8.30 rows=1 width=250) (actual time=0.559..0.561 rows=1 loops=1)
  Index Cond: (id = 50000)
Planning Time: 0.892 ms
Execution Time: 0.591 ms
```

#### Consulta por `UUIDv4`
```text
Index Scan using orders_uuid_pkey on orders_uuid  (cost=0.41..8.43 rows=1 width=258) (actual time=0.014..0.014 rows=0 loops=1)
  Index Cond: (id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid)
Planning Time: 0.130 ms
Execution Time: 0.039 ms
```

> **Análise**: Ambas as estratégias utilizam **`Index Scan`** com alta eficiência para buscas pontuais de um único registro. Contudo, em consultas que envolvam *range scans* (intervalos) ou ordenação temporal, o `BIGINT` leva vantagem expressiva devido à localidade física dos dados no disco.

---

## 🎯 Conclusão e Recomendações de Arquitetura

1. **Quando utilizar BIGINT**: Sistemas transacionais genéricos (OLTP) de alta performance onde não há necessidade de esconder a quantidade de registros inseridos e a criação de IDs acontece centralizada no banco de dados.
2. **Quando utilizar UUIDv4**: Sistemas distribuídos ou arquiteturas de microsserviços onde a geração do ID precisa ocorrer na camada da aplicação antes da persistência, sem coordenação central.
3. **Alternativa Recomendada (UUIDv7)**: Para mitigar os impactos do `UUIDv4` sem perder a unicidade distribuída, adota-se o **UUIDv7**, que embute um *timestamp* de milissegundos nos primeiros bits do UUID, garantindo ordenação natural e eliminando a fragmentação por *page splits* no PostgreSQL.