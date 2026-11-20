package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Nunca expõe o tenant_id. Datas sempre em UTC. */
public record AgendamentoResponse(
        UUID id,
        UUID profissionalId,
        UUID servicoItemId,
        String nomeCliente,
        String profissionalNome,
        String servicoNome,
        String emailCliente,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        StatusAgendamento status) {

    public static AgendamentoResponse de(Agendamento agendamento) {
        return new AgendamentoResponse(
                agendamento.getId(),
                agendamento.getProfissional().getId(),
                agendamento.getServicoItem().getId(),
                agendamento.getNomeCliente(),
                agendamento.getProfissional().getNome(),
                agendamento.getServicoItem().getNome(),
                agendamento.getEmailCliente(),
                agendamento.getInicio().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getFim().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getStatus());
    }
}
