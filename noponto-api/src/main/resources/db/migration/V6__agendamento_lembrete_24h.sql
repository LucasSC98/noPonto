-- Coluna para controle idempotente de envio do lembrete de 24h antes do agendamento.

ALTER TABLE agendamento
    ADD COLUMN IF NOT EXISTS lembrete_enviado_em TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_agendamento_lembrete_pendente
    ON agendamento (status, horario_inicio)
    WHERE lembrete_enviado_em IS NULL;
