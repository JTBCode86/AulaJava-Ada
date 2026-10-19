# Prática: Guias Oficiais e Experimentos de Engenharia de Release (Trilha Prática)

> **Sobre este guia:**
>
> Este documento reúne **tutoriais e documentações oficiais** (Docker, Google Distroless, Paketo Buildpacks, Aqua Trivy, CycloneDX e Flyway) para praticar empacotamento OCI, auditoria de vulnerabilidades na esteira e migrações versionadas de banco de dados.
>
> Cada trilha destaca primeiro os **links e tutoriais recomendados para estudo e prática guiada**, seguidos de um bloco **Extra (CLI)** — todos utilizando um único repositório clássico ([spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic)) para que você possa executar e validar cada ferramenta localmente em poucos minutos.

---

## 🗺️ Matriz: Desafios da Aula vs. Guias e Tutoriais

| Desafio Técnico | Tutorial / Documentação Recomendada | O que você pratica / inspeciona |
| :--- | :--- | :--- |
| **Separar compilação (JDK) da execução (JRE)** | [Docker Guide: Containerize a Java App](https://docs.docker.com/guides/java/containerize/) | Criação de Multi-Stage build e cache de camadas Maven no `spring-petclinic`. |
| **Reduzir superfície de ataque (sem shell/apt)** | [GoogleContainerTools: Distroless](https://github.com/GoogleContainerTools/distroless) | Empacotamento em imagem mínima (`:nonroot`), smoke test e diagnóstico com `:debug`. |
| **Gerar imagem OCI sem escrever Dockerfile** | [Spring Boot Buildpacks (Paketo)](https://docs.spring.io/spring-boot/maven-plugin/build-image.html) | Empacotamento automatizado em camadas via `spring-boot:build-image`. |
| **Auditar CVEs (App vs SO) e gerar SBOM** | [Trivy Getting Started](https://trivy.dev/docs/latest/getting-started/) & [CycloneDX Maven](https://github.com/CycloneDX/cyclonedx-maven-plugin) | Escaneamento comparativo (SO vs Java) e geração de inventário CycloneDX. |
| **Versionar e evoluir schema de banco com segurança** | [Guide to Flyway & Spring Boot](https://reflectoring.io/database-migration-spring-boot-flyway/) & [Expand and Contract](https://www.prisma.io/dataguide/types/relational/expand-and-contract-pattern) | Execução de migrations (`V1` a `V5`), trava de *checksum* e evolução sem downtime. |
| **Automatizar a esteira (PR a Release)** | [GitHub Actions Java Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven) & [GitHub Skills Sandbox](https://github.com/skills/test-with-actions) | Orquestração de workflow YAML com cache Maven, testes, SBOM, auditoria Trivy e sandbox local com `act`. |

---

## 1. Trilha: Empacotamento OCI, Multi-Stage, Distroless e Paketo

O objetivo desta trilha é construir imagens Java enxutas e imutáveis — comparando um `Dockerfile` Multi-Stage com imagem base *Distroless* e a geração automatizada via **Cloud Native Buildpacks (Paketo)**.

### 📚 Links e Tutoriais: Empacotamento OCI

* **Tutorial Oficial Docker para Java (Multi-Stage):** [Containerize a Java application (Docker Docs)](https://docs.docker.com/guides/java/containerize/)
  * *O que praticar:* Tutorial passo a passo oficial utilizando o projeto **Spring PetClinic** para estruturar um `Dockerfile` Multi-Stage, separando o estágio de compilação (Maven + JDK) do estágio de execução (JRE) com extração de camadas.
* **Repositório Oficial Google Distroless:** [GoogleContainerTools/distroless](https://github.com/GoogleContainerTools/distroless)
  * *O que estudar:* Documentação na raiz do repositório explicando por que imagens `gcr.io/distroless/java21-debian12` contêm apenas a JRE e dependências mínimas do sistema (sem `sh`, `bash` ou `apt`), por que usar a tag `:nonroot` em produção e como funciona a variante `:debug`.
* **Alternativa sem Dockerfile — Paketo Buildpacks:** [Spring Boot Maven Plugin: Packaging OCI Images](https://docs.spring.io/spring-boot/maven-plugin/build-image.html) e [Paketo Java Buildpack Docs](https://paketo.io/docs/howto/java/)
  * *O que praticar:* Uso do comando nativo `./mvnw spring-boot:build-image`, que utiliza o **Paketo Buildpacks** para converter o projeto em uma imagem OCI otimizada em camadas sem necessidade de `Dockerfile`, aplicando usuário não-root, cálculo automático de memória da JVM e SBOM embutido.

### 💻 Extra — Empacotamento OCI no PetClinic (CLI)

1. Clone o repositório oficial [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic):

   ```bash
   git clone https://github.com/spring-projects/spring-petclinic.git
   cd spring-petclinic
   ```

2. **Abordagem A — Gerando imagem OCI sem `Dockerfile` (via Paketo Buildpacks):**

   ```bash
   ./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=spring-petclinic:paketo
   ```

   > *Detalhe técnico:* Ao listar a imagem com `docker images`, note que a data de criação aparece como **40+ anos atrás (`1980-01-01`)**. O Paketo zera os *timestamps* dos arquivos de propósito para garantir **Reproducible Builds**: o mesmo código e as mesmas dependências geram exatamente o mesmo hash SHA-256 em qualquer máquina.

3. **Abordagem B — Criando um `Dockerfile` Multi-Stage + Distroless:**
   Crie um arquivo `Dockerfile` na raiz do `spring-petclinic`:

   ```dockerfile
   # Estágio 1: Build (Maven + JDK 21) -> descartado na imagem final
   FROM eclipse-temurin:21-jdk-jammy AS builder
   WORKDIR /build
   COPY --chmod=0755 mvnw mvnw
   COPY .mvn/ .mvn/
   COPY pom.xml .
   COPY src ./src
   RUN ./mvnw package -DskipTests

   # Estágio 2: Runtime (Apenas JRE 21 Distroless, rodando como nonroot)
   FROM gcr.io/distroless/java21-debian12:nonroot
   WORKDIR /app
   COPY --from=builder /build/target/*.jar /app/app.jar
   USER nonroot
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "/app/app.jar"]
   ```

4. Construa e inspecione tamanho, ausência de terminal, variante `:debug` e hashes:

   ```bash
   # 1. Construa a imagem Distroless
   docker build -t spring-petclinic:distroless .

   # 2. Compare o tamanho das imagens construídas
   docker images spring-petclinic

   # 3. Comprove a ausência de terminal na Distroless (:nonroot) -> deve falhar ("sh": executable file not found)
   docker run --rm -it --entrypoint sh spring-petclinic:distroless

   # 4. Veja como a variante :debug da Distroless inclui um shell busybox apenas para diagnóstico local
   docker run --rm -it --entrypoint sh gcr.io/distroless/java21-debian12:debug

   # 5. Inspecione o Image ID local (hash SHA-256 da configuração local da imagem)
   docker inspect --format='{{.Id}}' spring-petclinic:distroless
   ```

   > *Nota de Engenharia (`Image ID` vs `RepoDigest`):* O campo `{{.Id}}` acima é o identificador local da imagem descompactada. Quando a imagem é publicada em um Container Registry (`docker push`), o registry calcula o **`RepoDigest`** (`{{index .RepoDigests 0}}`, no formato `app@sha256:...`), que é o hash imutável do manifesto OCI usado no Kubernetes/Helm para promover o artefato entre ambientes.

5. **Smoke Test da Imagem Construída:**
   Comprove na esteira que a imagem Distroless inicia e responde com sucesso antes de autorizar qualquer abertura de tráfego:

   ```bash
   # Inicie o container em background
   docker run -d --name petclinic-smoke -p 8080:8080 spring-petclinic:distroless

   # Aguarde o startup e valide a resposta de saúde (retorna 200)
   sleep 20
   curl -fsS -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health

   # Limpe o container de teste
   docker rm -f petclinic-smoke
   ```

---

## 2. Trilha: Auditoria de Vulnerabilidades (App vs SO) e SBOM

Na esteira de CI/CD, separamos vulnerabilidades introduzidas pelas dependências da aplicação (`pom.xml`) daquelas presentes nos pacotes do sistema operacional da imagem base, além de emitir a lista de materiais de software (**SBOM**).

### 📚 Links e Tutoriais: Auditoria de CVEs e SBOM

* **Guia Oficial do Aqua Trivy:** [Trivy Getting Started](https://trivy.dev/docs/latest/getting-started/) e [Trivy Container Image Scanning](https://trivy.dev/docs/latest/guide/target/container_image/)
  * *O que praticar:* Instalação do scanner e execução de auditorias em imagens de container filtrando por severidade (`HIGH,CRITICAL`) e diferenciando pacotes do SO de bibliotecas da linguagem.
* **SBOM no Build Java (Via Plugin Maven):** [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin)
  * *O que praticar:* Geração do inventário CycloneDX diretamente pelo ciclo de vida do Maven (`makeAggregateBom`), aproveitando o grafo exato de resolução de dependências do `pom.xml` (já configurado nativamente no `spring-petclinic`).
* **SBOM Genérico na Imagem OCI:** [Trivy SBOM Generation](https://trivy.dev/docs/latest/guide/supply-chain/sbom/)
  * *O que praticar:* Extração de SBOM diretamente a partir de qualquer imagem Docker/OCI pronta, inventariando tanto pacotes de sistema operacional quanto artefatos `.jar` empacotados na imagem.

### 💻 Extra — Auditoria de CVEs e SBOM no PetClinic (CLI)

Ainda dentro do diretório `spring-petclinic`, execute os comandos abaixo *(se não tiver o binário `trivy` instalado localmente no SO, substitua `trivy` por `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest`)*:

```bash
# 1. Gerar o SBOM das dependências Java direto pelo Maven (gera target/bom.json com ~106 componentes)
./mvnw cyclonedx:makeAggregateBom

# 2. Comparar vulnerabilidades de SO: imagem JDK completa vs imagem Distroless
trivy image eclipse-temurin:21-jdk-jammy
trivy image spring-petclinic:distroless

# 3. Simular Gate de CI: interromper a pipeline (exit code 1) apenas se houver CVE CRÍTICO COM correção disponível
trivy image --exit-code 1 --severity CRITICAL --ignore-unfixed spring-petclinic:distroless

# 4. Gerar SBOM genérico da imagem OCI completa (Pacotes Linux + JRE + App Java) no formato CycloneDX
trivy image --format cyclonedx --output sbom-image.cdx.json spring-petclinic:distroless
```

> *O que observar no passo 2:* Ao comparar os dois relatórios do Trivy, repare que as vulnerabilidades de **sistema operacional (Debian/Ubuntu)** caem drasticamente na imagem `distroless`, mas eventuais vulnerabilidades nas **bibliotecas Java (`pom.xml`)** continuam idênticas — comprovando por que usar Distroless não substitui o SCA das dependências da aplicação.

---

## 3. Trilha: Migrações de Banco de Dados com Flyway e Evolução sem Downtime

O objetivo desta trilha é praticar a mecânica do **Flyway** (versionamento sequencial `V1__`, tabela de auditoria `flyway_schema_history` e trava de integridade por *checksum*) e simular o ciclo completo de evolução no padrão **Expand and Contract**.

### 📚 Links e Tutoriais: Migrações de Banco de Dados

* **Tutorial Completo (Flyway + Spring Boot):** [One-Stop Guide to Database Migration with Flyway and Spring Boot (Reflectoring)](https://reflectoring.io/database-migration-spring-boot-flyway/)
  * *O que estudar:* Como o Flyway aplica scripts SQL em ordem estrita, como funciona a validação de *checksum* na tabela `flyway_schema_history`, como corrigir desvios com `repair` e como acionar migrações na esteira de CI/CD.
* **Documentação Oficial Spring Boot:** [Execute Flyway Database Migrations on Startup (Spring Boot Docs)](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
  * *O que consultar:* Propriedades nativas `spring.flyway.*` e convenção de diretórios `classpath:db/migration`.
* **Guia Visual de Migração sem Downtime:** [The Expand and Contract Pattern (Prisma Data Guide)](https://www.prisma.io/dataguide/types/relational/expand-and-contract-pattern) e [Parallel Change (Martin Fowler)](https://martinfowler.com/bliki/ParallelChange.html)
  * *O que estudar:* Como dividir mudanças destrutivas de banco (como renomear ou substituir colunas) nas fases *Expand → Migrate (Dual-Write/Backfill) → Contract* para que réplicas das versões `v1` e `v2` da aplicação operem simultaneamente durante o deploy com rollback imediato.

### 💻 Extra — Expand-Contract e Trava de Checksum no Flyway (CLI)

Ainda dentro do diretório `spring-petclinic`, vamos utilizar os scripts SQL reais do projeto junto com o container oficial `flyway/flyway` e um banco H2 persistido em arquivo para observar a evolução segura de schema e a trava de integridade:

```bash
# 1. Prepare o diretório copiando o schema e dados iniciais do PetClinic como V1 e V2:
mkdir -p target/flyway-lab/sql
cp src/main/resources/db/h2/schema.sql target/flyway-lab/sql/V1__init_schema.sql
cp src/main/resources/db/h2/data.sql   target/flyway-lab/sql/V2__seed_data.sql

# 2. Execute o Flyway aplicando V1 e V2 em um banco persistido em disco (target/flyway-lab/petclinic-db.mv.db):
docker run --rm -u "$(id -u):$(id -g)" -v "$(pwd)/target/flyway-lab:/flyway/work" \
  flyway/flyway:11-alpine \
  -url=jdbc:h2:file:/flyway/work/petclinic-db -user=sa -password= \
  -locations=filesystem:/flyway/work/sql migrate

# 3. FASE 1 (EXPAND - V3): Adicione a nova coluna contato_telefone NULLABLE (sem quebrar queries da v1)
cat << 'EOF' > target/flyway-lab/sql/V3__expand_add_contato.sql
ALTER TABLE owners ADD COLUMN contato_telefone VARCHAR(20) NULL;
EOF

# 4. FASE 2 (BACKFILL & DUAL-WRITE - V4): Popule a nova coluna a partir dos dados existentes
cat << 'EOF' > target/flyway-lab/sql/V4__backfill_contato.sql
UPDATE owners SET contato_telefone = telephone WHERE contato_telefone IS NULL;
EOF

# 5. Aplique V3 e V4 (o Flyway detecta que V1 e V2 já rodaram e aplica apenas o delta):
docker run --rm -u "$(id -u):$(id -g)" -v "$(pwd)/target/flyway-lab:/flyway/work" \
  flyway/flyway:11-alpine \
  -url=jdbc:h2:file:/flyway/work/petclinic-db -user=sa -password= \
  -locations=filesystem:/flyway/work/sql migrate

# 6. Inspecione a tabela flyway_schema_history (veja as 4 versões aplicadas com sucesso):
docker run --rm -u "$(id -u):$(id -g)" -v "$(pwd)/target/flyway-lab:/flyway/work" \
  flyway/flyway:11-alpine \
  -url=jdbc:h2:file:/flyway/work/petclinic-db -user=sa -password= \
  -locations=filesystem:/flyway/work/sql info

# 7. FASE 3 (CONTRACT - V5): Executado em release futura (após v2 estar estável e v1 desativada)
cat << 'EOF' > target/flyway-lab/sql/V5__contract_drop_telephone.sql
ALTER TABLE owners DROP COLUMN telephone;
EOF

docker run --rm -u "$(id -u):$(id -g)" -v "$(pwd)/target/flyway-lab:/flyway/work" \
  flyway/flyway:11-alpine \
  -url=jdbc:h2:file:/flyway/work/petclinic-db -user=sa -password= \
  -locations=filesystem:/flyway/work/sql migrate

# 8. Teste a regra de ouro (migrations aplicadas são imutáveis): adultere a V1 já aplicada
echo "-- alteracao indevida" >> target/flyway-lab/sql/V1__init_schema.sql

docker run --rm -u "$(id -u):$(id -g)" -v "$(pwd)/target/flyway-lab:/flyway/work" \
  flyway/flyway:11-alpine \
  -url=jdbc:h2:file:/flyway/work/petclinic-db -user=sa -password= \
  -locations=filesystem:/flyway/work/sql validate
# Resultado: "ERROR: Validate failed: Migration checksum mismatch for migration version 1"
```

> *Por que o Rollback é seguro na Fase 2?*
> Enquanto a versão `v2` estiver em Canary gravando em ambas as colunas (`telephone` e `contato_telefone`), caso surja um bug grave e a imagem seja revertida para a `v1`, as instâncias antigas continuam encontrando todos os dados atualizados em `telephone`. O banco nunca quebra e nenhuma venda é perdida.

---

## 4. Trilha: Orquestração de CI/CD com GitHub Actions (Sandbox & Workflows Reais)

O objetivo desta trilha é conectar as etapas exploradas nas trilhas anteriores (build Java, testes, SBOM CycloneDX, empacotamento OCI e auditoria Trivy) em uma esteira declarativa no **GitHub Actions**, experimentando tanto em um ambiente sandbox interativo quanto localmente com o **`act`**.

### 📚 Links, Tutoriais e Sandboxes: GitHub Actions

* **Sandbox Interativo Oficial — GitHub Skills:** [Test with GitHub Actions (GitHub Skills)](https://github.com/skills/test-with-actions)
  * *O que praticar:* Laboratório hands-on interativo e gratuito mantido pelo GitHub que gera um repositório dedicado na sua conta. Você aprende a disparar workflows via Pull Request, inspecionar logs de execução e configurar branch protections com status checks obrigatórios sem precisar gerenciar infraestrutura.
* **Tutorial Oficial de CI para Java com Maven:** [Building and Testing Java with Maven (GitHub Docs)](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
  * *O que estudar:* Como estruturar pipelines para Java, utilizar a action oficial `actions/setup-java@v4` com distribuição Temurin e habilitar cache automático do repositório local do Maven (`cache: 'maven'`) para acelerar execuções subsequentes.
* **Auditoria de Vulnerabilidades no Actions (Trivy Action):** [aquasecurity/trivy-action](https://github.com/aquasecurity/trivy-action)
  * *O que consultar:* Como plugar o scanner Aqua Trivy no workflow para barrar PRs com severidade `CRITICAL` e publicar relatórios no GitHub Code Scanning (formato SARIF).
* **Sandbox Local sem Gastar Cota — `act`:** [nektos/act (Run GitHub Actions locally)](https://github.com/nektos/act)
  * *O que praticar:* Ferramenta que utiliza o Docker local para executar os runners e steps do GitHub Actions na sua própria máquina, permitindo iterar e validar arquivos `.github/workflows/*.yml` instantaneamente sem gerar commits desnecessários nem consumir minutos de runner na nuvem.

### 💻 Extra — Workflow Integrado no PetClinic e Sandbox Local com `act` (CLI)

Ainda no diretório `spring-petclinic`, crie o pipeline completo amarrando os portões de validação da aplicação e do container:

1. Crie o arquivo `.github/workflows/ci.yml`:

   ```yaml
   name: CI Checkout Service

   on:
     push:
       branches: [ main ]
     pull_request:
       branches: [ main ]

   jobs:
     build-and-audit:
       runs-on: ubuntu-latest
       steps:
         - name: Checkout do Código
           uses: actions/checkout@v4

         - name: Configurar JDK 21 (Temurin com Cache Maven)
           uses: actions/setup-java@v4
           with:
             java-version: '21'
             distribution: 'temurin'
             cache: 'maven'

         - name: Compilar e Executar Testes
           run: ./mvnw -B test

         - name: Gerar SBOM de Dependências (CycloneDX)
           run: ./mvnw -B cyclonedx:makeAggregateBom

         - name: Construir Imagem Docker Multi-Stage
           run: docker build -t spring-petclinic:ci .

         - name: Auditoria de Vulnerabilidades na Imagem (Trivy)
           uses: aquasecurity/trivy-action@master
           with:
             image-ref: 'spring-petclinic:ci'
             format: 'table'
             exit-code: '1'
             ignore-unfixed: true
             vuln-type: 'os,library'
             severity: 'CRITICAL'
   ```

2. **Como Executar esse Workflow Localmente (Sandbox via `act`):**

   ```bash
   # 1. Instale o 'act' (ex: via curl, brew ou baixando o binário oficial):
   # curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash

   # 2. Liste os jobs e eventos mapeados no workflow local:
   act -l

   # 3. Simule um Pull Request localmente no Docker (executando o job build-and-audit):
   act pull_request -j build-and-audit
   ```

   > *Por que usar o `act` como sandbox?* Ele sobe imagens de container equivalentes aos runners oficiais do GitHub (`ubuntu-latest`) diretamente no Docker local. Isso elimina o ciclo vicioso de *"git commit -m 'tentativa 2 de consertar o CI'"* e permite validar a sintaxe e a integridade de cada step antes de abrir o Pull Request oficial.
