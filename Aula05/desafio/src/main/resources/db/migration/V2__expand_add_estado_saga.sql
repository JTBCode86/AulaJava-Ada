-- FASE 1: EXPAND (Adiciona nova coluna permitindo NULL)
ALTER TABLE pagamentos ADD COLUMN IF NOT EXISTS estado_saga VARCHAR(50);