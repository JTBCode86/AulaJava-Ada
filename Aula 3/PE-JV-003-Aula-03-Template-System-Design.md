# System Design: Aula 03 - Banco de Dados em Larga Escala

> **Contexto do Cenário: Checkout na Black Friday**
>
> Sua equipe está preparando a persistência da API de Checkout para o pico de tráfego da Black Friday:
>
> 1. **Armazenamento:** Transações financeiras de pedidos exigem garantias ACID rigorosas e consistência imediata, enquanto dados de catálogo e sessões de usuários demandam alta vazão de leitura e tolerância a partição.
> 2. **Concorrência:** A atualização de status do pedido tem baixa disputa por registro (apenas o comprador interage), mas a **reserva de estoque** sofre altíssima contenção com centenas de clientes disputando as últimas unidades do mesmo SKU promocional.
> 3. **Incidente em Produção:** Em testes de carga, o tempo de resposta p99 subiu para 4.8s e o pool HikariCP saturou devido a consultas lentas de pedidos (`WHERE customer_id = ?` e `WHERE order_status = 'processing'`).

 **Instruções:** Responda às decisões arquiteturais abaixo fundamentando suas escolhas estritamente com base nos requisitos e restrições deste cenário. Adicione este bloco ao repositório contínuo do seu projeto.
---

## 1. Decisão de Armazenamento e Chave Primária (PK)

### 1.1 Motor de Banco de Dados e Paradigma de Persistência

- **A Escolha para o Checkout:** ( ) Relacional Puro (PostgreSQL) (X) Persistência Poliglota / Híbrida
- **Justificativa Arquitetural:** Embora o núcleo transacional financeiro e de pedidos exija rigor ACID inegociável (consistência e atomicidade imediatas sob o Teorema CAP, focando em CP para o ledger de pagamentos), uma abordagem puramente relacional centralizada gargalharia sob a volumetria de leitura da Black Friday. A adoção de uma **Persistência Poliglota / Híbrida** permite isolar domínios: o PostgreSQL (com particionamento e réplicas de leitura) gerencia o core transacional de pedidos e pagamentos com garantia ACID estrita, enquanto armazenamentos complementares absorvem leituras intensas de catálogo e sessões, garantindo alta vazão sem corromper o estado financeiro.
- **Além do Modelo Documental (Ecossistema NoSQL):** A persistência poliglota vai muito além da dicotomia "Relacional vs MongoDB". Considerando as necessidades complementares de uma plataforma de e-commerce, destacam-se:
  1. **Banco Chave-Valor (In-Memory / Ex: Redis):** Ideal para gerenciar o carrinho de compras efêmero, sessões de usuários e camadas de cache distribuído de preços/catálogo. Oferece operações atômicas em memória de baixa latência ($O(1)$) e suporte a TTL, aliviando o banco relacional de picos de leitura frequentes e transiente.
  2. **Banco de Busca / Colunar Textual (Ex: Elasticsearch / OpenSearch):** Ideal para a engine de busca e catálogo de produtos da Black Friday. Permite buscas textuais complexas, filtros multifacetados e agregação de SKUs em milissegundos, algo ineficiente e custoso de realizar via `LIKE` em motores relacionais tradicionais sob alta concorrência.

### 1.2 Estratégia da Chave Primária

- **A Escolha:** ( ) Sequence / Identity ( ) UUIDv4 (X) UUIDv7 / TSID
- **Impacto no Armazenamento:** O uso de sequências centralizadas gera contenção e gargalos em escritas distribuídas de alta volumetria, enquanto o UUIDv4 totalmente randômico causa fragmentação severa de páginas nas B-Trees do PostgreSQL devido aos *page splits* aleatórios em inserções massivas. O **UUIDv7** resolve esse dilema ao incorporar um componente temporal (timestamp) nos bits mais significativos, garantindo que as chaves sejam geradas de forma quase sequencial no tempo. Isso assegura que novas inserções ocorram de forma *append-only* nas folhas da B-Tree, otimizando o uso de buffer pools e eliminando o overhead de rebalanceamento de páginas na Black Friday.

---

## 2. A Dualidade da Concorrência: Locks e Mitigações

Analise e defina a abordagem de concorrência para os dois fluxos críticos do negócio:

### 2.1 Mudança de Status do Pagamento (Baixa contenção por registro)

- **Abordagem de Lock:** (X) Lock Otimista (`@Version`) ou ( ) Lock Pessimista (`SELECT FOR UPDATE`)?
- **Justificativa:** Como apenas o comprador interage diretamente com o seu próprio pedido e a chance de duas transações concorrentes alterarem o status do mesmo pedido simultaneamente é estatisticamente irrelevante (baixa contenção por registro), o **Lock Otimista** via coluna de versão (`@Version`) é ideal. Ele evita o custo de manter bloqueios ativos em nível de linha no banco de dados, maximizando o *throughput* de atualizações de status vindas dos webhooks de pagamento.

### 2.2 Controle de Estoque Concorrente (Alta contenção no mesmo registro)

- **Abordagem de Lock:** ( ) Lock Otimista (`@Version`) ou (X) Lock Pessimista (`SELECT FOR UPDATE`)?
- **Justificativa:** Sob altíssima contenção (centenas de requisições simultâneas para o mesmo SKU promocional), o Lock Otimista geraria uma cascata de exceções (`OptimisticLockException`) e *rollbacks* seguidos de *retries* frustrantes. O **Lock Pessimista (`SELECT FOR UPDATE NOWAIT` ou com timeout estrito)** serializa de forma segura o acesso à linha do estoque daquele SKU específico. Para evitar que o banco tombe, o controle de estoque deve ser particionado em shards lógicos por armazém/corredor ou desacoplado em filas de processamento atômico (ex: decremento em memória via Redis com Lua Scripts antes da consolidação assíncrona no banco).

### 2.3 Riscos e Mitigações

- **Análise Crítica:** 
  - *Riscos do Lock Pessimista:* Saturação do pool de conexões (HikariCP) e *deadlocks* devido a esperas prolongadas por locks de linha.
  - *Mitigação:* Uso rigoroso de timeouts curtos nas queries (`statement_timeout`), ordem estrita de aquisição de locks em operações multi-item, e principalente, o uso de uma camada de controle de estoque desacoplada (como decremento atômico em memória ou filas controladas por token bucket) na borda, impedindo que requisições excedentes sequer alcancem o banco relacional e gerem contenção de threads.

---

## 3. Troubleshooting do Incidente e Gestão de Índices

### 3.1 Investigação de Incidente em Produção

- **Cenário:** Em testes de carga para a Black Friday, o tempo de resposta p99 do Checkout subiu repentinamente para 4.8s e o pool de conexões (HikariCP) saturou. Os logs apontam que a consulta de pedidos mais frequente (`WHERE customer_id = ? AND order_status = 'processing'`) está demorando segundos para responder.
- **Plano de Diagnóstico:** 
  1. **Identificar Queries Ativas:** Executar uma consulta na view `pg_stat_activity` para isolar queries em execução de longa duração ou travadas em locks de espera (`waiting`).
  2. **Analisar o Plano de Execução:** Executar `EXPLAIN ANALYZE, BUFFERS SELECT * FROM orders WHERE customer_id = ? AND order_status = 'processing';` para verificar se o otimizador está realizando um *Seq Scan* (varredura sequencial) em vez de utilizar um índice adequado.
  3. **Monitorar Métricas de I/O e Cache:** Verificar o índice de acerto do buffer cache (`blks_read` vs `blks_hit`) e estatísticas de uso de índices na tabela (`pg_stat_user_tables` / `pg_stat_user_indexes`) para confirmar se a consulta está sofrendo de I/O excessivo em disco devido à ausência de índices compostos.

### 3.2 Decisão de Engenharia: A Abordagem de Índices

- **Dilema no Pull Request:** Para solucionar a lentidão, um desenvolvedor da equipe abriu um PR criando índices individuais para todas as colunas da tabela de pedidos que aparecem em filtros de consulta.
- **Seu Posicionamento:** **Não aprovaria o PR da forma como foi estruturado.** Criar índices *individuais* isolados para cada coluna (`customer_id` separado de `order_status`) é ineficiente para consultas compostas com `AND`, pois o PostgreSQL precisará fazer uma operação de *Bitmap Index Scan* combinando múltiplos índices, o que degrada a performance sob alta carga. 
- **A estratégia correta:** Em vez de múltiplos índices simples, deve-se criar um **Índice Composto (B-Tree)** cobrindo as colunas na ordem de seletividade (`CREATE INDEX idx_orders_cust_status ON orders (customer_id, order_status);`). Além disso, deve-se ponderar que cada índice adicional introduz custo de escrita (*overhead* em operações de `INSERT`, `UPDATE` e `DELETE`) e consome espaço em disco/memória, exigindo monitoramento contínuo de índices não utilizados (`pg_stat_user_indexes`).