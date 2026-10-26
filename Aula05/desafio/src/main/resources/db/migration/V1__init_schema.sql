CREATE TABLE IF NOT EXISTS pagamentos (
                                          id UUID PRIMARY KEY,
                                          valor NUMERIC(19, 2) NOT NULL,
    status_pagamento VARCHAR(50) NOT NULL,
    data_criacao TIMESTAMP NOT NULL
    );