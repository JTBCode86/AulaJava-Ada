# 🚀 Serviço de Checkout: Engenharia de Release, Containers & Zero-Downtime

Este projeto é uma implementação de referência de um serviço de **Checkout** resiliente e seguro, projetado com foco em arquitetura de microsserviços, **Engenharia de Release**, empacotamento OCI seguro, auditoria de vulnerabilidades em esteiras CI/CD e evolução de banco de dados sem downtime (*Zero-Downtime Deploy*) através do padrão **Expand and Contract**.

---

## 📂 Estrutura do Repositório (`desafio/`)

```text
desafio/
├── .github/
│   └── workflows/
│       └── ci.yml                 # Pipeline GitHub Actions (Build, Testes, SBOM e Trivy)
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── adatest/
│       │           └── checkout/
│       │               ├── CheckoutApplication.java
│       │               ├── controller/
│       │               │   └── PagamentoController.java
│       │               ├── domain/
│       │               │   ├── Pagamento.java
│       │               │   └── StatusPagamento.java
│       │               └── repository/
│       │                   └── PagamentoRepository.java
│       └── resources/
│           ├── application.yml
│           └── db/
│               └── migration/
│                   ├── V1__init_schema.sql
│                   ├── V2__expand_add_estado_saga.sql
│                   └── V3__backfill_estado_saga.sql
├── Dockerfile                      # Multi-Stage Build (Eclipse Temurin JDK 21 -> Distroless JRE 21)
├── docker-compose.yml              # SubIDA e orquestração local (PostgreSQL 16 + API)
├── pom.xml                         # Dependências Spring Boot & Plugin CycloneDX
└── README.md                       # Documentação do projeto
```

---

## 🛠️ Tecnologias e Decisões de Arquitetura

- **Linguagem & Framework:** Java 21 LTS com Spring Boot 3.2.x e Spring Data JPA.
- **Banco de Dados:** PostgreSQL 16.
- **Gerenciamento de Migrações:** Flyway com versionamento sequencial imutável.
- **Empacotamento OCI Seguro:** Multi-stage build utilizando **Google Distroless (`gcr.io/distroless/java21-debian12:nonroot`)** como imagem final. Não contém shell, pacotes desnecessários ou usuário `root`, mitigando execuções arbitrárias de código.
- **Segurança & Governança (DevSecOps):** 
  - **CycloneDX Maven Plugin**: Geração automática de SBOM (*Software Bill of Materials*).
  - **Aqua Trivy Scanner**: Análise estática no pipeline buscando vulnerabilidades de severidade `CRITICAL` e `HIGH` na imagem container.
- **Zero-Downtime Database Migration:** Aplicação do padrão *Expand and Contract* com suporte a *Dual Write* no modelo domain.

---

## 🔄 Padrão Expand and Contract (Zero-Downtime Migration)

Para realizar a alteração no esquema do banco de dados sem exigir janela de manutenção ou interrupção de serviço, o ciclo de migração é dividido em etapas controladas:

1. **Fase 1: Expand (`V2__expand_add_estado_saga.sql`)**
   - Adiciona a nova coluna `estado_saga` permitindo valores nulos (`NULLABLE`).
   - Permite que versões antigas da aplicação continuem funcionando sem quebras de contrato no banco.

2. **Fase de Transição (Dual Write em Código):**
   - A aplicação Java é atualizada para ler da nova coluna e gravar em ambas as colunas (`status_pagamento` e `estado_saga`) sincronizadamente.

3. **Fase 2: Backfill (`V3__backfill_estado_saga.sql`)**
   - Executa a migração e população assíncrona dos registros legados em segundo plano, sem travar tabelas em produção.

4. **Fase 3: Contract (Próximo Release):**
   - Adiciona restrições `NOT NULL` e remove a coluna legada após validar que 100% do tráfego utiliza a nova estrutura.

---

## 🚀 Como Executar o Projeto Localmente

### Pré-requisitos
- Docker Engine e Docker Compose instalados.
- Java 21 e Maven 3.9+ (caso queira executar fora do container).

### 1. Inicializar Infraestrutura e Aplicação
Dentro da pasta `desafio/`, execute:

```bash
docker compose up --build -d
```

O Docker Compose inicializará o banco PostgreSQL, executará o healthcheck e construirá a imagem da aplicação Checkout via Multi-Stage Build.

### 2. Verificar os Containers em Execução
```bash
docker compose ps
```

### 3. Validar Healthcheck (Smoke Test)
```bash
curl -i http://localhost:8080/api/pagamentos/healthz
```

**Resposta esperada:**
```json
{"service":"checkout-service","status":"UP"}
```

### 4. Testar a Criação e Listagem de Pagamentos
- **Criar Pagamento:**
  ```bash
  curl -X POST "http://localhost:8080/api/pagamentos?valor=250.50"
  ```

- **Listar Pagamentos:**
  ```bash
  curl http://localhost:8080/api/pagamentos
  ```

---

## 🛡️ Pipeline de CI/CD (GitHub Actions)

A esteira declarativa definida em `.github/workflows/ci.yml` garante a qualidade e segurança do artefato a cada *Push* ou *Pull Request*:

1. **Build e Testes Unitários:** Executa a compilação e validação do código via `./mvnw test`.
2. **Software Bill of Materials (SBOM):** Gera o inventário de componentes em formato CycloneDX (`target/bom.json`).
3. **Container Build:** Constrói a imagem Docker baseada em Google Distroless.
4. **Vulnerability Scan (Trivy):** Analisa a imagem OCI e quebra o build (`exit-code 1`) se houver vulnerabilidades críticas não corrigidas.