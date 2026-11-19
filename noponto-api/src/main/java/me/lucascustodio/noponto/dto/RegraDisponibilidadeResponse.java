package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.RegraDisponibilidade;

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.UUID;

public record RegraDisponibilidadeResponse(
        UUID id,
        UUID profissionalId,
        DayOfWeek diaSemana,
        OffsetTime horaInicio,
        OffsetTime horaFim,
        Boolean ativo
) {

    public static RegraDisponibilidadeResponse de(RegraDisponibilidade regra) {
        return new RegraDisponibilidadeResponse(
                regra.getId(),
                regra.getProfissional().getId(),
                regra.getDiaSemana(),
                regra.getHoraInicio(),
                regra.getHoraFim(),
                regra.getAtivo()
        );
    }
}
