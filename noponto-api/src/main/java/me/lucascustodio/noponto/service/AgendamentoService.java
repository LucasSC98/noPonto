package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.exception.HorarioIndisponivelExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final ProfissionalService profissionalService;
    private final SlotService slotService;
    private final TenantService tenantService;
    private final Clock relogio;

    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                              ProfissionalService profissionalService,
                              SlotService slotService,
                              TenantService tenantService,
                              Clock relogio) {
        this.agendamentoRepository = agendamentoRepository;
        this.profissionalService = profissionalService;
        this.slotService = slotService;
        this.tenantService = tenantService;
        this.relogio = relogio;
    }

    /**
     * Cria o agendamento de forma transacional. Primeiro trava (SELECT FOR UPDATE) a linha do profissional,
     * o que serializa criações concorrentes para o mesmo profissional; só então recalcula se o slot ainda
     * está livre. Travar apenas linhas de agendamento não protegeria quando ainda não há nenhuma no horário.
     * Profissional ou serviço inativo rejeita o novo agendamento sem alterar os já existentes.
     */
    @Transactional
    public AgendamentoResponse criar(CriarAgendamentoRequest request, UUID tenantId) {
        Profissional profissional = profissionalService.buscarAtivoComBloqueio(tenantId, request.profissionalId());
        ServicoItem servico = profissionalService
                .buscarServicoParaAgendamento(tenantId, profissional.getId(), request.servicoItemId());

        OffsetDateTime inicio = request.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        if (!slotService.slotDisponivel(profissional.getId(), inicio, tenantId, servico.getId())) {
            throw new HorarioIndisponivelExcecao();
        }

        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente(request.nomeCliente().trim());
        agendamento.setEmailCliente(request.emailCliente().trim());
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plus(Duration.ofMinutes(servico.getDuracaoMinutos())));
        agendamento.setStatus(StatusAgendamento.PENDENTE);

        return AgendamentoResponse.de(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse atualizarStatus(UUID agendamentoId, StatusAgendamento novoStatus, UUID tenantId) {
        Agendamento agendamento = agendamentoRepository.findByIdAndTenantId(agendamentoId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Agendamento não encontrado."));

        StatusAgendamento statusAtual = agendamento.getStatus();
        if (statusAtual == StatusAgendamento.CONCLUIDO) {
            throw new RegraNegocioExcecao("Agendamento concluído não pode ser alterado.");
        }
        if (!statusAtual.transicoesPermitidas().contains(novoStatus)) {
            throw new RegraNegocioExcecao(
                    "Transição de status inválida: " + statusAtual + " -> " + novoStatus + ".");
        }

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        if (novoStatus == StatusAgendamento.CANCELADO) {
            // A antecedência mínima vale para o cliente. O admin da clínica cancela sem essa trava.
            agendamento.setCanceladoEm(agora);
        }
        agendamento.setStatus(novoStatus);

        return AgendamentoResponse.de(agendamentoRepository.save(agendamento));
    }

    /**
     * Regra do cancelamento feito pelo cliente. O admin usa {@link #atualizarStatus} e não passa por aqui.
     */
    public void validarAntecedenciaCancelamento(Agendamento agendamento, UUID tenantId, OffsetDateTime agora) {
        int antecedenciaMinutos = tenantService.buscarPorId(tenantId).getAntecedenciaMinimaCancelamentoMinutos();
        OffsetDateTime limite = agendamento.getInicio().minusMinutes(antecedenciaMinutos);
        if (agora.isAfter(limite)) {
            throw new RegraNegocioExcecao(
                    "Cancelamento permitido somente com antecedência mínima de " + antecedenciaMinutos + " minutos.");
        }
    }
}
