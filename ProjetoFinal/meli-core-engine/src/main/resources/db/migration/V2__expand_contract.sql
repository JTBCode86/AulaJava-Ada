-- ==============================================================================
-- MIGRAÇÃO DE BANCO DE DADOS: PATTERN EXPAND & CONTRACT
-- Objetivo: Adicionar a coluna buyer_tax_id mantendo retrocompatibilidade.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- FASE 1: EXPAND (Expansão)
-- Adiciona a nova coluna como NULLABLE para não quebrar a versão V1 da API que 
-- ainda não envia este campo.
-- ------------------------------------------------------------------------------

ALTER TABLE orders_seq 
ADD COLUMN buyer_tax_id VARCHAR(20) DEFAULT NULL;

ALTER TABLE orders_uuid 
ADD COLUMN buyer_tax_id VARCHAR(20) DEFAULT NULL;

-- ------------------------------------------------------------------------------
-- FASE 2: BACKFILL (Preenchimento de dados legados)
-- Atualiza registros existentes que possuem a nova coluna como NULL para evitar
-- erros na futura fase de Contração (Contract).
-- ------------------------------------------------------------------------------

UPDATE orders_seq 
SET buyer_tax_id = '000.000.000-00' 
WHERE buyer_tax_id IS NULL;

UPDATE orders_uuid 
SET buyer_tax_id = '000.000.000-00' 
WHERE buyer_tax_id IS NULL;

-- ------------------------------------------------------------------------------
-- NOTA TÉCNICA (FASE 3 - CONTRACT):
-- A alteração para aplicar NOT NULL (Contract) será feita em uma migração V3,
-- após garantir que 100% das instâncias da API estejam atualizadas e escrevendo
-- o novo campo obrigatoriamente.
-- ------------------------------------------------------------------------------