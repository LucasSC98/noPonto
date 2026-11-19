package me.lucascustodio.noponto.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum StatusAgendamento {
    PENDENTE,
    CONFIRMADO,
    CANCELADO,
    CONCLUIDO;

    /** Status que ocupam o horário do profissional no cálculo de slots. */
    public static List<StatusAgendamento> ocupamHorario() {
        return List.of(PENDENTE, CONFIRMADO);
    }

    /** Transições permitidas a partir deste status. CANCELADO e CONCLUIDO são finais (imutáveis). */
    public Set<StatusAgendamento> transicoesPermitidas() {
        return switch (this) {
            case PENDENTE -> EnumSet.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> EnumSet.of(CONCLUIDO, CANCELADO);
            case CANCELADO, CONCLUIDO -> EnumSet.noneOf(StatusAgendamento.class);
        };
    }
}
