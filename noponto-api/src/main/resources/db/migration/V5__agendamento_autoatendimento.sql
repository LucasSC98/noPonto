-- Colunas para autoatendimento do cliente (token com hash SHA-256, origem do cancelamento e indicador de remarcação)
-- Nunca armazena o token puro no banco, apenas seu hash SHA-256 em hexadecimal (64 caracteres).

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS antecedencia_minima_cancelamento_minutos INTEGER NOT NULL DEFAULT 120;

ALTER TABLE agendamento
    ADD COLUMN IF NOT EXISTS token_gerenciamento_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cancelado_pelo_cliente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS remarcado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_agendamento_token_hash
    ON agendamento (token_gerenciamento_hash)
    WHERE token_gerenciamento_hash IS NOT NULL;
