package me.lucascustodio.noponto.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Resposta HTTP 409 retornada quando um bloqueio sobrepõe agendamentos ativos sem confirmação. */
public record ConflitoBloqueioResponse(
        int status,
        String mensagem,
        OffsetDateTime momento,
        List<AgendamentoConflitanteResponse> agendamentosConflitantes) {
}
