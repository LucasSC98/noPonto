-- As regras gravadas antes da correção do fuso ficaram em UTC com o relógio
-- deslocado em 3 horas (08:00-03:00 foi persistido como 11:00+00).
-- O relógio digitado volta, agora com o offset de Brasília. O filtro evita
-- aplicar a correção de novo em linhas que já estão em -03.

UPDATE regra_disponibilidade
SET hora_inicio = (
        (substring(hora_inicio::text FROM 1 FOR 8)::time - INTERVAL '3 hours')::time::text || '-03'
    )::timetz,
    hora_fim = (
        (substring(hora_fim::text FROM 1 FOR 8)::time - INTERVAL '3 hours')::time::text || '-03'
    )::timetz
WHERE EXTRACT(TIMEZONE FROM hora_inicio) = 0
  AND EXTRACT(TIMEZONE FROM hora_fim) = 0;
