-- Horários das regras de disponibilidade passam a guardar o offset (OffsetTime / TIMETZ).
-- O offset define o fuso em que o dia da semana e o horário de atendimento são interpretados
-- (ex.: 08:00-03:00 = 08h no horário de Brasília). A migration V1 não possuía dados nessa tabela.

ALTER TABLE regra_disponibilidade
    ALTER COLUMN hora_inicio TYPE TIMETZ USING (hora_inicio::TIMETZ),
    ALTER COLUMN hora_fim TYPE TIMETZ USING (hora_fim::TIMETZ);
