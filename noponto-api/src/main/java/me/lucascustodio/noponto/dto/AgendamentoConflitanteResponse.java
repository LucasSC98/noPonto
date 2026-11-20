package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Resumo de um agendamento ativo que conflita com um bloqueio solicitado. Datas em UTC. */
public record AgendamentoConflitanteResponse(
        UUID id,
        String nomeCliente,
        String servicoNome,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        StatusAgendamento status) {

    public static AgendamentoConflitanteResponse de(Agendamento agendamento) {
        return new AgendamentoConflitanteResponse(
                agendamento.getId(),
                agendamento.getNomeCliente(),
                agendamento.getServicoItem().getNome(),
                agendamento.getInicio().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getFim().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getStatus());
    }
}
