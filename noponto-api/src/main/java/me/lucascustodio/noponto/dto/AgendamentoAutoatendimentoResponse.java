package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Resumo público do agendamento consultado via token de autoatendimento.
 * Nunca expõe {@code tenant_id}, hash do token, nem e-mail/telefone do profissional.
 */
public record AgendamentoAutoatendimentoResponse(
        UUID id,
        UUID profissionalId,
        String profissionalNome,
        String profissionalEspecialidade,
        UUID servicoItemId,
        String servicoNome,
        int servicoDuracaoMinutos,
        String nomeEstabelecimento,
        String subdominioEstabelecimento,
        String nomeCliente,
        String emailCliente,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        StatusAgendamento status,
        int antecedenciaMinimaCancelamentoMinutos,
        boolean remarcado) {

    public static AgendamentoAutoatendimentoResponse de(Agendamento agendamento, Tenant tenant) {
        return new AgendamentoAutoatendimentoResponse(
                agendamento.getId(),
                agendamento.getProfissional().getId(),
                agendamento.getProfissional().getNome(),
                agendamento.getProfissional().getEspecialidade(),
                agendamento.getServicoItem().getId(),
                agendamento.getServicoItem().getNome(),
                agendamento.getServicoItem().getDuracaoMinutos(),
                tenant != null ? tenant.getNome() : "NoPonto",
                tenant != null ? tenant.getSubdominio() : null,
                agendamento.getNomeCliente(),
                agendamento.getEmailCliente(),
                agendamento.getInicio().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getFim().withOffsetSameInstant(ZoneOffset.UTC),
                agendamento.getStatus(),
                tenant != null && tenant.getAntecedenciaMinimaCancelamentoMinutos() != null
                        ? tenant.getAntecedenciaMinimaCancelamentoMinutos()
                        : Tenant.ANTECEDENCIA_MINIMA_CANCELAMENTO_PADRAO_MINUTOS,
                Boolean.TRUE.equals(agendamento.getRemarcado()));
    }
}
