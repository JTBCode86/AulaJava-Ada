# Template de System Design: Aula 05 - Containers, CI/CD & Deploy sem Downtime

> **Contexto do Cenário: O Deploy que Quebrou o Checkout e o Rollback Impossível**
>
> Sua equipe está reestruturando a engenharia de release do serviço de **Checkout** após um incidente severo na Black Friday:
>
> 1. **O Incidente:** Para subir uma otimização urgente na Saga, o pipeline de produção recompilou a branch `main`, sobrescreveu a tag `checkout:latest` e iniciou a atualização. No boot da primeira instância nova, o **Flyway** renomeou a coluna `status_pagamento` para `estado_saga`.
> 2. **O Efeito em Cadeia:** Os 80% de servidores que ainda rodavam a versão anterior passaram a falhar imediatamente (`column "status_pagamento" does not exist`). Ao tentar acionar o rollback puxando `:latest`, a equipe descobriu que a imagem anterior havia sido sobrescrita e, mesmo restaurando um backup do binário antigo, ele continuou falhando porque o banco já estava alterado.
> 3. **A Carga:** 5.000 TPS ativos com múltiplas réplicas de aplicação conectadas ao mesmo banco PostgreSQL.
>
> **Instruções:** Responda às decisões arquiteturais abaixo fundamentando suas escolhas estritamente com base nos requisitos e restrições deste cenário. Documente suas decisões no repositório contínuo do seu projeto através de commits atômicos e bem descritivos.

---

## 1. Empacotamento, Superfície de Ataque e Promoção Imutável

### 1.1 Anatomia da Imagem e Redução de Superfície de Ataque

- **Construção vs Execução:** Como você estruturará o empacotamento do serviço de Checkout para produção? Especifique o que deve ser usado na etapa de compilação, o que deve (e o que não deve) entrar na imagem final de execução, com qual permissão de usuário o processo rodará e qual o trade-off da sua escolha de imagem base para a segurança e para o diagnóstico em produção.
  > **Estratégia de Build Multi-stage:** A construção utilizará o padrão *Multi-stage Build*. Na primeira etapa (Build Stage), empregaremos uma imagem com o SDK completo e ferramentas de compilação/shell (`dotnet SDK`, `maven`, `go build`). Na etapa final de execução (Runtime Stage), apenas o binário compilado e os assets estritamente necessários serão copiados para uma imagem base limpa.
  > 
  > **O que entra vs. O que é excluído:** O SDK, código-fonte bruto, gerenciadores de pacotes e ferramentas de shell (`bash`, `sh`, `curl`, `wget`) **não entram** na imagem final. Isso reduz drasticamente a superfície de ataque, eliminando vulnerabilidades em pacotes e utilitários não utilizados.
  > 
  > **Permissões e Usuário:** O processo da aplicação rodará obrigatoriamente sob um usuário não-privilegiado (ex: `nonroot` ou UID dedicado), impedindo escalação de privilégios caso ocorra uma falha de segurança na aplicação.
  > 
  > **Trade-off da Imagem Base (Distroless / Alpine / Slim):** A escolha por imagens *Distroless* oferece máxima segurança (zero utilitários de sistema e ausência de shell), mas impõe um trade-off no diagnóstico em produção, pois impede o acesso via `kubectl exec -it ... sh` para debugging interativo. O diagnóstico passa a depender exclusivamente de observabilidade avançada (Structured JSON Logging, métricas Prometheus e rastreamento distribuído OpenTelemetry).

### 1.2 Rastreabilidade, Identidade do Artefato e Parametrização

- **Da Homologação à Produção:** Como sua esteira garante que o binário executado em produção é exatamente o mesmo que passou nos testes de homologação? Explique como as configurações mudam entre ambientes (dev, staging, prod) sem recompilar o artefato e como você identifica a versão no registry para que um rollback nunca puxe um binário sobrescrito por engano.
  > **Imutabilidade do Binário (Build Once, Deploy Many):** O binário e a imagem de container são construídos **uma única vez** logo após a aprovação do Pull Request na branch `main`. O mesmo artefato gerado e testado em homologação (Staging) é promovido para produção. A esteira é terminantemente proibida de recompilar o código na virada para produção.
  > 
  > **Injeção de Configuração:** As configurações específicas de cada ambiente (variáveis de conexão com banco de dados, endpoints de mensageria, timeouts) são desacopladas do código e injetadas puramente em tempo de execução (*Run-time*), através de variáveis de ambiente (`ENV`) ou gerenciadores de segredos seguros (AWS Secrets Manager / Vault) montados pelo orquestrador (Kubernetes ConfigMaps/Secrets).
  > 
  > **Identidade e Versionamento Imutável:** O uso da tag `:latest` é banido de forma definitiva do ecossistema. Cada build recebe uma tag única, imutável e rastreável baseada no hash curto do commit do Git e no número sequencial do pipeline (ex: `checkout:v1.4.2-sha.a1b2c3d`). O registro de containers (`Registry`) é configurado com políticas de retenção e proteção contra sobrescrita (`immutable tags`), garantindo que um rollback sempre aponte para um hash de imagem estático, histórico e garantidamente idêntico ao validado anteriormente.

---

## 2. Estratégia de Rollout e Migração de Banco sem Downtime

### 2.1 O Modelo de Rollout em Produção

- **A Escolha:** ( X ) Canary Release (Liberação Gradual por Percentual)  ( ) Blue-Green Deployment (Alternância de Ambientes)
- **Justificativa e Mitigação das Desvantagens do seu Modelo:**
  > Sob uma carga massiva de **5.000 TPS**, a estratégia de **Canary Release** é a escolha ideal para mitigar o impacto de falhas imprevistas, pois expõe apenas uma fração mínima dos usuários à nova versão.
  > 
  > **Evolução do Tráfego sob 5.000 TPS:** A liberação ocorrerá de forma automatizada e gradual: 1% -> 5% -> 20% -> 50% -> 100%, com janelas de permanência controladas em cada patamar para absorção volumétrica de requisições.
  > 
  > **Sinais de Avanço ou Rollback Automático (Guardrails de Observabilidade):** O pipeline monitorará em tempo real métricas vitais via Prometheus/Datadog: taxa de erro HTTP 5xx, latência de p99 do endpoint de pagamento e contagem de estornos na Saga. Se a taxa de erros exceder 0.5% ou a latência subir em mais de 15% em relação à baseline, o Canary abortará instantaneamente, realizando o rollback automatizado para a versão estável anterior.
  > 
  > **Coexistência de Versões (Backward/Forward Compatibility):** Como duas versões da API atenderão clientes simultaneamente durante a janela de canary, o contrato de API e os payloads de mensageria (Kafka/Outbox) devem manter estrita compatibilidade retroativa e prospectiva (adicionando campos opcionais e evitando remoções/renomeações abruptas de atributos).

### 2.2 Evolução de Schema Sem Downtime e Plano de Rollback

- **Passo a Passo da Migração:** Descreva em etapas sequenciais como evoluir a coluna `status_pagamento` para `estado_saga` no PostgreSQL garantindo que as instâncias da versão antiga (`v1`) e da versão nova (`v2`) operem juntas sem erros de SQL.
  > Para evitar o desastre do incidente narrado (onde o Flyway renomeou a coluna quebrando a `v1`), a evolução de schema segue obrigatoriamente o padrão de **Expand and Contract (Expand-Contract Pattern)** em múltiplas fases desacopladas dos deploys de aplicação:
  > 
  > 1. **Fase 1 (Expand - Adição):** Cria-se uma migração de banco para adicionar a nova coluna `estado_saga` como *nullable* (ou com valor default compatível) na tabela de pagamentos. A versão antiga (`v1`) continua lendo e escrevendo exclusivamente em `status_pagamento`.
  > 2. **Fase 2 (Dual Write / Bridge):** Faz-se o deploy da versão intermediária ou da `v2` programada para escrever simultaneamente em ambas as colunas (`status_pagamento` e `estado_saga`), garantindo que leituras e escrituras não deixem lacunas. 
  > 3. **Fase 3 (Migração de Dados Históricos):** Um job de background idempotente migra gradualmente os dados legados da coluna antiga para a nova coluna.
  > 4. **Fase 4 (Chaveamento de Leitura):** Atualiza-se a aplicação para ler prioritariamente de `estado_saga`.
  > 5. **Fase 5 (Contract - Limpeza):** Apenas após a adoção de 100% da `v2` e estabilização do sistema em produção, uma nova migration futura remove a coluna obsoleta `status_pagamento`.
- **Garantia de Rollback Imediato:** Caso a versão `v2` apresente degradação grave de performance com 20% do tráfego na Black Friday, qual é o procedimento exato de rollback e por que o seu desenho de banco de dados garante que a versão `v1` voltará a assumir 100% da carga sem quebrar nem perder dados gravados pela `v2`?
  > Como a coluna antiga `status_pagamento` foi mantida intacta na base de dados (graças ao padrão Expand-Contract e à proibição de operações destrutivas como `DROP COLUMN` ou `RENAME COLUMN` em deploys de aplicação), o rollback do container para a versão `v1` ocorre de forma instantânea e totalmente segura. A versão `v1` voltará a assumir 100% do tráfego e continuará operando normalmente sobre a coluna `status_pagamento` sem gerar erros de schema (`column does not exist`), preservando todos os dados transacionais processados durante a janela de canary.

---

## 3. Guardrails de Segurança, Governança e Validação da Release

### 3.1 Gates de Segurança e Guardrails no Processo de Entrega

- **Guardrails do Commit ao Deploy:** Quais portões automatizados de qualidade e segurança devem proteger a sua esteira ao longo do fluxo de desenvolvimento (desde o Pull Request até a publicação e promoção da imagem)?
  > - **Análise Estática de Código (SAST):** SonarQube / Semgrep bloqueando PRs com vulnerabilidades críticas ou code smells de alta severidade.
  > - **Scan de Dependências e SBOM:** Verificação de bibliotecas de terceiros com CVEs conhecidas (Trivy / Snyk).
  > - **Scan de Imagem de Container:** Validação de vulnerabilidades do sistema operacional base da imagem antes de enviá-la ao Registry privado.
  > - **Testes de Contrato (Pact):** Garantindo que as alterações de API do Checkout não quebrem os serviços consumidores da Saga.

- **Governança e Controle em Produção:** Quais *guardrails* de processo e arquitetura impedem que alterações manuais não rastreadas cheguem à produção e como equilibrar segurança rigorosa com agilidade de resposta durante incidentes críticos sem perder a trilha de auditoria?
  > **Bloqueio de Acesso Manual (Immutable Infrastructure):** Nenhuma alteração direta via `kubectl` ou acesso SSH em servidores de produção é permitida. Todo o estado de produção é declarative-driven (GitOps via ArgoCD/Flux).
  > 
  > **Agilidade em Incidentes (Break-Glass Protocol):** Para cenários de crise severa, adota-se o protocolo de *Break-Glass* — um mecanismo auditado onde credenciais temporárias de privilégio elevado são concedidas mediante aprovação dupla de engenheiros seniores, gerando logs imutáveis e alertas automáticos aos canais de segurança e auditoria.

### 3.2 Estratégia de Testes da Imagem e da Migração

- **Validação Pré-Tráfego:** Passar nos testes unitários durante o build do código não prova que a imagem final do container sobe nem que a migração de banco é retrocompatível.
- **Plano de Testes na Pipeline:** Descreva como você testará automaticamente a imagem construída e validará a coexistência da migração de banco de dados na esteira antes de liberar o deploy para o tráfego real de clientes.
  > A esteira incorporará um ambiente de homologação efêmero (Sandbox/Ephemeral Environment) executando instâncias reais de banco de dados (PostgreSQL via contêineres Docker isolados no pipeline):
  > 
  > 1. **Teste de Subida do Container:** O pipeline executa um smoke test automatizado (`docker run --rm ... healthcheck`) para certificar-se de que a imagem final inicializa com sucesso e responde ao endpoint de saúde (`/healthz`) sem falhas de dependência de runtime.
  > 2. **Simulação de Coexistência de Versões (Backward Compatibility Test):** O pipeline aplica as migrações de banco (`Flyway`), roda instâncias simultâneas da versão legada (`v1`) e da nova versão (`v2`) conectadas à mesma base de dados de teste, e executa uma suíte de testes de integração ponta a ponta (*Integration Test Suite*). Isso valida de forma prática que ambas as versões operam em harmonia antes que qualquer artefato toque os ambientes produtivos da Black Friday.