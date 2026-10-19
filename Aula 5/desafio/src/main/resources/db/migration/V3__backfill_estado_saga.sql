-- FASE 2: BACKFILL (Popula os dados legados)
UPDATE pagamentos
SET estado_saga = status_pagamento
WHERE estado_saga IS NULL;