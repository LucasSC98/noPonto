package me.lucascustodio.noponto.notificacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DadosEmailAgendamento(
        UUID tenantId,
        UUID agendamentoId,
        String nomeEstabelecimento,
        String fusoHorarioTenant,
        String nomeCliente,
        String emailCliente,
        String nomeProfissional,
        String nomeServico,
        OffsetDateTime inicioUtc,
        OffsetDateTime fimUtc,
        String linkAutoatendimento) {
}
