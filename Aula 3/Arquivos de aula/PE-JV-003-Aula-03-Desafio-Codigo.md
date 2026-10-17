# Prática: Diagnóstico e Otimização de Queries no PostgreSQL

> **Como conduzir este laboratório:**
>
> * **Você criará as estruturas por conta própria:** Este documento não traz comandos `CREATE INDEX` prontos. O fluxo esperado em cada desafio é: diagnosticar o plano inicial com `EXPLAIN (ANALYZE, BUFFERS)`, formular sua hipótese técnica, **escrever e executar no banco o comando de criação da estrutura/índice** e reavaliar a mesma consulta para comprovar empiricamente a alteração dos nós e o ganho de desempenho.
> * **Gabarito de referência:** As discussões arquiteturais completas e soluções de referência estão no repositório de gabaritos.
> * **Entregável oficial:** O entregável avaliativo da aula permanece sendo o documento de System Design no repositório de cada aluno(a).

---

## Cenário do Incidente

Na Aula 02, aplicamos proteções de rede (Circuit Breaker e Rate Limiter) na borda da API de Checkout. A aplicação passou a suportar rajadas de tráfego sem derrubar as instâncias de serviço.

Durante um evento de pico de vendas, o monitoramento acusou degradação severa no banco de dados:

* Latência p99 do Checkout subiu de 25ms para 4,8 segundos.
* O pool de conexões do HikariCP esgotou por espera (`ConnectionTimeoutException`).
* A CPU da instância primária do PostgreSQL atingiu 100%, dominada por I/O de disco.
* Consultas operacionais e relatórios do painel começaram a disputar recursos com transações de pagamento.

O objetivo desta prática é reproduzir gargalos reais de concorrência e latência, inspecionar o comportamento interno do banco com `EXPLAIN (ANALYZE, BUFFERS)` e avaliar criticamente se as consultas e a estrutura de dados estão adequadas para sustentar a escala de produção.

---

## Seção 1 - Setup e Importação dos Dados

### 1. Escolha do Ambiente

Escolha a opção mais conveniente para executar os testes:

* **Opção 1 (Recomendada - Local via Docker + DBeaver):**
  * Suba uma instância da versão estável mais recente do PostgreSQL (17) via Docker. A referência de parâmetros está na página da [imagem oficial do Postgres no Docker Hub](https://hub.docker.com/_/postgres).
  * **Atenção ao mapeamento de porta (`-p 5432:5432`):** configure a exposição de porta para permitir a conexão a partir do host e defina sua própria senha:

    ```bash
    docker run --name pg-aula03 \
      -e POSTGRES_PASSWORD=<sua_senha> \
      -p 5432:5432 \
      -d postgres:17 \
      -c shared_preload_libraries=pg_stat_statements \
      -c pg_stat_statements.track=all
    ```

  * Conecte pelo DBeaver (Host: `localhost`, Porta: `5432`, Banco: `postgres`, Usuário: `postgres`, Senha: `<sua_senha_definida>`).
  * **Formas de inspecionar o plano no DBeaver:**
    * *Modo Texto (Recomendado):* digite `EXPLAIN (ANALYZE, BUFFERS) SELECT ...` e execute normalmente com `Ctrl + Enter`. A saída textual completa aparecerá na aba de resultados (e pode ser colada em [explain.dalibo.com](https://explain.dalibo.com)).
    * *Modo Gráfico (`Ctrl + Shift + E`):* selecione ou posicione o cursor apenas na consulta `SELECT ...` **sem** a palavra `EXPLAIN` no início. O DBeaver adiciona a instrução de explain automaticamente por baixo dos panos; se você mantiver a palavra `EXPLAIN` na seleção ao pressionar o atalho, o banco retornará erro de sintaxe SQL (`syntax error at or near "EXPLAIN"`).
* **Opção 2 (Navegador - Zero Setup):**
  * [database.build](https://database.build): PostgreSQL rodando direto no navegador via WebAssembly (PGlite).
  * [DB-Fiddle](https://www.db-fiddle.com): Escolha a versão mais recente do PostgreSQL. Cole o DDL e a carga no painel esquerdo (*Schema SQL*) e execute os testes no painel direito (*Query SQL*).
* **Opção 3 (Nuvem Gratuita - Neon.tech):** Crie uma instância PostgreSQL gratuita no [Neon.tech](https://neon.tech) e conecte pelo DBeaver.

---

### 2. Criação do Schema Relacional

Execute o DDL abaixo para criar as tabelas de pedidos, itens e pagamentos, com suas respectivas chaves primárias e estrangeiras:

```sql
CREATE TABLE olist_orders (
    order_id VARCHAR(32) PRIMARY KEY,
    customer_id VARCHAR(32) NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    order_purchase_timestamp TIMESTAMP NOT NULL,
    order_approved_at TIMESTAMP,
    order_delivered_carrier_date TIMESTAMP,
    order_delivered_customer_date TIMESTAMP,
    order_estimated_delivery_date TIMESTAMP
);

CREATE TABLE olist_order_items (
    order_id VARCHAR(32) NOT NULL,
    order_item_id INT NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    seller_id VARCHAR(32) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    freight_value NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (order_id, order_item_id),
    CONSTRAINT fk_order_items_orders FOREIGN KEY (order_id) REFERENCES olist_orders (order_id) ON DELETE CASCADE
);

CREATE TABLE olist_order_payments (
    order_id VARCHAR(32) NOT NULL,
    payment_sequential INT NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    payment_installments INT NOT NULL,
    payment_value NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (order_id, payment_sequential),
    CONSTRAINT fk_order_payments_orders FOREIGN KEY (order_id) REFERENCES olist_orders (order_id) ON DELETE CASCADE
);
```

---

### 3. Como Obter e Importar o Dataset do Olist

Utilizaremos o dataset público de e-commerce brasileiro da Olist.

#### Passo 3.1: Download dos Arquivos

Baixe o dataset na fonte pública oficial ([Kaggle - Brazilian E-Commerce Public Dataset by Olist](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce)). Extraia do arquivo compactado apenas os três arquivos necessários para o laboratório:

1. `olist_orders_dataset.csv` (~15 MB, ~99.441 linhas)
2. `olist_order_items_dataset.csv` (~16 MB, ~112.650 linhas)
3. `olist_order_payments_dataset.csv` (~6 MB, ~103.886 linhas)

#### Passo 3.2: Passo a Passo de Importação no DBeaver

Para respeitar as Foreign Keys, a ordem de importação é mandatória: **primeiro a tabela pai (`olist_orders`), depois as filhas (`olist_order_items` e `olist_order_payments`)**.

1. No painel esquerdo do DBeaver (**Database Navigator**), expanda sua conexão, vá em **Schemas -> public -> Tables**.
2. Clique com o botão direito na tabela **`olist_orders`** e selecione **Import Data**.
3. Na janela que abrir, escolha o formato **CSV** e clique em **Next**.
4. No campo de arquivo, clique no botão de busca e aponte para o arquivo `olist_orders_dataset.csv` no seu computador.
5. Em **Importer settings**, verifique se:
   * O delimitador está configurado como vírgula (`,`).
   * O delimitador de texto está configurado como aspas duplas (`"`).
   * O encoding está definido como `UTF-8`.
6. Clique em **Next**, confira a tela de mapeamento de colunas e clique em **Proceed**.
7. Aguarde a barra de progresso concluir a importação (leva cerca de 10 a 20 segundos).
8. Repita o mesmo procedimento para a tabela **`olist_order_items`** apontando para `olist_order_items_dataset.csv`.
9. Repita o mesmo procedimento para a tabela **`olist_order_payments`** apontando para `olist_order_payments_dataset.csv`.

---

### 4. Alternativa Expressa: Carga Instantânea via SQL

Caso esteja utilizando um sandbox web (DB-Fiddle ou database.build) ou prefira não baixar os CSVs, execute o bloco abaixo para injetar 100 mil pedidos, 200 mil itens e 100 mil pagamentos gerados na hora:

```sql
-- 100.000 pedidos
INSERT INTO olist_orders (
    order_id, customer_id, order_status, 
    order_purchase_timestamp, order_estimated_delivery_date
)
SELECT 
    md5(random()::text),
    md5((random() * 10000)::int::text),
    CASE WHEN random() < 0.90 THEN 'delivered' ELSE (ARRAY['shipped', 'canceled', 'invoiced'])[floor(random() * 3 + 1)] END,
    NOW() - (random() * 90) * INTERVAL '1 day',
    NOW() + (random() * 15) * INTERVAL '1 day'
FROM generate_series(1, 100000);

-- 200.000 itens (2 itens por pedido)
INSERT INTO olist_order_items (
    order_id, order_item_id, product_id, seller_id, price, freight_value
)
SELECT 
    o.order_id,
    item_seq,
    md5((random() * 5000)::int::text),
    md5((random() * 500)::int::text),
    (random() * 300 + 15)::numeric(10, 2),
    (random() * 40 + 10)::numeric(10, 2)
FROM olist_orders o
CROSS JOIN generate_series(1, 2) AS item_seq;

-- 100.000 pagamentos
INSERT INTO olist_order_payments (
    order_id, payment_sequential, payment_type, payment_installments, payment_value
)
SELECT 
    o.order_id,
    1,
    (ARRAY['credit_card', 'boleto', 'pix', 'voucher'])[floor(random() * 4 + 1)],
    floor(random() * 6 + 1)::int,
    (random() * 600 + 30)::numeric(10, 2)
FROM olist_orders o;
```

---

### 5. Ativação da Observabilidade (pg_stat_statements)

Com os dados carregados na base (seja pelos arquivos CSV ou pela carga em SQL), ative a extensão de monitoramento e zere os contadores para iniciar a prática com o histórico limpo:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Limpa os registros de carga para monitorar exclusivamente as queries dos desafios:
SELECT pg_stat_statements_reset();
```

---

## Seção 2 - Desafios Práticos de Troubleshooting

> Regra do laboratório: execute cada consulta com `EXPLAIN (ANALYZE, BUFFERS)` em seu estado original antes de tomar qualquer ação no banco. Utilize o visualizador gráfico do DBeaver (`Ctrl + Shift + E`) ou cole a saída JSON em [explain.dalibo.com](https://explain.dalibo.com) para analisar tempo de execução, nós do plano e consumo de buffers (I/O).

---

### Desafio 1: Histórico de Pedidos por Cliente (Filtro Simples)

**Cenário em Produção:**

A API de histórico busca os pedidos feitos por um comprador específico.

Como os IDs variam de acordo com o método de carga (Kaggle ou sintética), execute primeiro uma consulta para capturar um `customer_id` existente na sua base:

```sql
-- 1. Obtenha um customer_id real:
SELECT customer_id 
FROM olist_orders 
LIMIT 1;
```

Em seguida, execute o diagnóstico substituindo o valor obtido (caso tenha importado o Kaggle completo, `9ef444f0dd1b6af545d3b1ae41e58249` é um exemplo funcional):

```sql
-- 2. Execute o diagnóstico:
EXPLAIN (ANALYZE, BUFFERS)
SELECT * 
FROM olist_orders 
WHERE customer_id = '9ef444f0dd1b6af545d3b1ae41e58249'; -- substitua pelo seu customer_id se necessário
```

**Análise e Investigação:**

* [ ] A consulta atual está adequada para suportar milhares de requisições por segundo no checkout de produção? Avalie o plano de execução (`tempo total`, `nós utilizados`, `linhas inspecionadas vs retornadas` e `buffers acessados`).
* [ ] Caso não esteja adequada, qual é a causa raiz da ineficiência e o que poderíamos fazer no banco de dados para melhorar esse cenário?
* [ ] Implemente sua proposta de melhoria. Ao rodar novamente o `EXPLAIN`, o que mudou no plano de execução e qual foi o impacto no tempo de resposta e no consumo de recursos?

---

### Desafio 2: Relatório de Gestão de Entregas (Filtros, Ordenação e Índice Composto)

**Cenário em Produção:**

A equipe de logística do marketplace utiliza uma tela para acompanhar pedidos entregues em um período recente, ordenados pelos mais novos com paginação fixa.

Se estiver usando o dataset do Kaggle (que contém dados de 2016 a 2018), consulte a data mais recente da sua base para parametrizar a janela de corte:

```sql
-- 1. Descubra a data mais recente da base:
SELECT MAX(order_purchase_timestamp) AS data_mais_recente 
FROM olist_orders;
```

Execute o diagnóstico parametrizando a janela (use `'2018-08-01'` para o dataset do Kaggle ou `NOW() - INTERVAL '30 days'` se usou carga sintética):

```sql
-- 2. Execute o diagnóstico da consulta:
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    order_id, 
    customer_id, 
    order_status, 
    order_purchase_timestamp
FROM olist_orders
WHERE order_status = 'delivered'
  AND order_purchase_timestamp >= '2018-08-01' -- se usou carga sintética, use: NOW() - INTERVAL '30 days'
ORDER BY order_purchase_timestamp DESC
LIMIT 20;
```

**Análise e Investigação:**

* [ ] Ao inspecionar o plano de execução desta consulta com `LIMIT 20`, quais operações ou nós chamam a atenção? O que acontece na etapa de ordenação (`Sort`)?
* [ ] Se tentarmos otimizar apenas os filtros individualmente (por exemplo, criando estruturas simples para cada coluna isolada), o nó de ordenação desaparece do plano de execução? Por que o banco ainda precisa ordenar os dados em memória?
* [ ] Qual estratégia você adotaria no banco de dados para atender tanto à filtragem quanto à ordenação em uma única estrutura composta?
* [ ] **Comparação de Planos (O gancho da ordenação):** Compare o `EXPLAIN (ANALYZE, BUFFERS)` antes e depois da criação da estrutura composta:
  * O nó explícito de `Sort` permaneceu ou foi eliminado do plano?
  * Como a forma de leitura mudou (necessidade de inspecionar todas as linhas candidatas para ordenar vs leitura já ordenada com parada antecipada no `LIMIT 20`)?
  * A ordem relativa das colunas dentro da estrutura composta alterou o plano ou a eficiência da ordenação? O que você observou na prática?

---

### Desafio 3: Painel do Lojista (Junções Complexas e Integridade Referencial)

**Cenário em Produção:**

Um vendedor acessa o painel para auditar as vendas recentes contendo seus produtos, cruzando pedidos, itens e modalidades de pagamento.

Para garantir que a consulta encontre dados reais e exercite as junções entre as tabelas, capture um `seller_id` ativo com bom volume de vendas:

```sql
-- 1. Obtenha um seller_id com pedidos registrados:
SELECT seller_id, COUNT(*) AS total_itens
FROM olist_order_items
GROUP BY seller_id
ORDER BY total_itens DESC
LIMIT 1;
```

Execute o diagnóstico com o identificador retornado (caso tenha importado o Kaggle completo, `3504c0c36d24e38854f069150ac5ed63` é um exemplo ativo):

```sql
-- 2. Execute o diagnóstico:
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    o.order_id,
    o.order_purchase_timestamp,
    o.order_status,
    i.product_id,
    i.price,
    p.payment_type
FROM olist_order_items i
JOIN olist_orders o ON i.order_id = o.order_id
JOIN olist_order_payments p ON o.order_id = p.order_id
WHERE i.seller_id = '3504c0c36d24e38854f069150ac5ed63' -- substitua pelo seu seller_id se necessário
ORDER BY o.order_purchase_timestamp DESC
LIMIT 20;
```

**Análise e Investigação:**

* [ ] A consulta com junções entre pedidos, itens e pagamentos está com desempenho aceitável para um painel web? Como o otimizador resolveu os cruzamentos de dados (`Join Type`) e quanto de I/O foi gerado?
* [ ] Caso a consulta não esteja adequada, quais mudanças você proporia na estrutura do banco para evitar leituras integrais de tabelas e permitir junções mais eficientes?
* [ ] Teste suas alterações e compare os planos antes e depois. Como os algoritmos de junção e o consumo de buffers reagiram?
* [ ] **Impacto colateral em operações de escrita:** No PostgreSQL, chaves estrangeiras (`Foreign Keys`) criam automaticamente estruturas auxiliares de busca nas tabelas filhas? O que acontece com a performance e concorrência do banco ao executar um `DELETE` ou `UPDATE` de chave na tabela pai (`olist_orders`)? (Para testar, capture um ID com `SELECT order_id FROM olist_orders LIMIT 1;`).

---

### Desafio 4: A Pegadinha da Baixa Seletividade

**Cenário em Produção:**

Analise duas consultas na mesma tabela `olist_orders`, considerando uma base onde a grande maioria dos pedidos é entregue com sucesso:

```sql
-- Consulta A: Status predominante (~90% da base)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * 
FROM olist_orders 
WHERE order_status = 'delivered';

-- Consulta B: Status raro (~3% a 4% da base)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * 
FROM olist_orders 
WHERE order_status = 'canceled';
```

**Análise e Investigação:**

* [ ] Crie uma estrutura auxiliar em `order_status` e execute ambas as consultas. O otimizador utilizou a estrutura nas duas situações?
* [ ] Por que o motor do banco decide ignorar o índice para `order_status = 'delivered'` e prefere fazer `Seq Scan`, enquanto para `order_status = 'canceled'` ele opta por utilizar a busca indexada?
* [ ] Em termos de arquitetura e leitura de disco, a partir de qual proporção de linhas retornadas (seletividade) a leitura sequencial da tabela passa a ser mais eficiente do que navegar por ponteiros em memória e disco (Random I/O)?

---

### Desafio 5: O Custo Oculto de Armazenamento e a Guerra dos IDs

**Cenário em Produção:**

Toda estrutura auxiliar criada para acelerar consultas gera impactos operacionais em disco e na taxa de escrita.

Execute o comando de auditoria de armazenamento no PostgreSQL:

```sql
SELECT 
    pg_size_pretty(pg_relation_size('olist_orders')) AS tamanho_tabela,
    pg_size_pretty(pg_indexes_size('olist_orders')) AS total_estruturas_auxiliares;
```

**Análise e Investigação:**

* [ ] Analisando o resultado do comando de auditoria, qual é o impacto em armazenamento gerado pelas estruturas criadas em relação ao tamanho bruto da tabela?
* [ ] Se cada estrutura auxiliar consome espaço e precisa ser atualizada a cada `INSERT`, `UPDATE` ou `DELETE`, qual é o custo operacional de manter estruturas em excesso durante um evento de alta volumetria como a Black Friday?
* [ ] No dataset do Olist, os identificadores são strings de 32 caracteres geradas aleatoriamente (padrão equivalente a UUIDv4). Como a inserção contínua de chaves não sequenciais afeta a organização interna das páginas do banco (*Page Splits*)?
* [ ] Qual alternativa moderna de identificador distribuído resolveria esse problema sem abrir mão da geração descentralizada de IDs?

---

### Desafio 6: Observabilidade em Produção com pg_stat_statements

**Cenário em Produção:**

Em produção, incidentes de lentidão não vêm acompanhados da query isolada pelo desenvolvedor. Os engenheiros consultam views de profiling estatístico em tempo real para identificar quais consultas estão sufocando a CPU e o disco do banco.

Como habilitamos o `pg_stat_statements` no setup inicial da aula, o PostgreSQL acumulou automaticamente as métricas de tempo, chamadas e blocos de cada query que você executou ao longo dos Desafios 1 a 5!

**Análise e Investigação:**

* [ ] Escreva uma consulta na view `pg_stat_statements` para auditar os 5 comandos que acumularam o maior tempo de execução (`total_exec_time`) no seu banco (exibindo `calls`, `total_exec_time`, `mean_exec_time`, `rows` e `query`). Dica: desconsidere as consultas na própria view filtrando `WHERE query NOT LIKE '%pg_stat_statements%'`.
* [ ] As consultas pesadas dos desafios anteriores apareceram no topo da lista? Quais métricas chamam a atenção?
* [ ] Em um plantão de incidentes, qual critério de ordenação revela melhor um problema de escala: o tempo total acumulado da query (`total_exec_time`) ou o tempo médio por execução (`mean_exec_time`)? Em quais cenários você priorizaria cada uma dessas métricas?
