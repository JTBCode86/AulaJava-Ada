-- 1. Criação das tabelas de teste
CREATE TABLE orders_sequential (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders_uuid (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Carga massiva de 100.000 registros e medição de tempo de inserção

-- Inserção Sequencial
EXPLAIN ANALYZE
INSERT INTO orders_sequential (customer_name, amount)
SELECT 'Cliente ' || g, (random() * 1000)::numeric(10,2)
FROM generate_series(1, 100000) g;

-- Inserção UUIDv4
EXPLAIN ANALYZE
INSERT INTO orders_uuid (customer_name, amount)
SELECT 'Cliente ' || g, (random() * 1000)::numeric(10,2)
FROM generate_series(1, 100000) g;

-- 3. Comparação de Tamanho das Tabelas e dos Índices
SELECT 
    relname AS tabela_ou_indice,
    pg_size_pretty(pg_total_relation_size(c.oid)) AS tamanho_total,
    pg_size_pretty(pg_indexes_size(c.oid)) AS tamanho_apenas_indices
FROM pg_class c
LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE relname IN ('orders_sequential', 'orders_uuid');

-- 4. Análise de Plano de Execução na Consulta (EXPLAIN ANALYZE)
EXPLAIN ANALYZE SELECT * FROM orders_sequential WHERE id = 50000;
EXPLAIN ANALYZE SELECT * FROM orders_uuid WHERE id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';