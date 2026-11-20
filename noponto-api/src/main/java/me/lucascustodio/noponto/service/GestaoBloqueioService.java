package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.dto.AgendamentoConflitanteResponse;
import me.lucascustodio.noponto.dto.BloqueioResponse;
import me.lucascustodio.noponto.dto.SalvarBloqueioRequest;
import me.lucascustodio.noponto.exception.ConflitoBloqueioExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Gerencia bloqueios (folgas, feriados, férias e indisponibilidades pontuais) do profissional.
 *
 * <p><strong>Tratamento de fuso horário:</strong> enquanto {@code RegraDisponibilidade} representa
 * janelas semanais recorrentes em {@code OffsetTime} ({@code TIMETZ}) para preservar o relógio
 * local no fuso do estabelecimento (ex.: {@code 09:00-03:00}), o {@link Bloqueio} representa um
 * período concreto no calendário {@code [inicio, fim)} e é persistido em {@code TIMESTAMPTZ} em
 * UTC. As entradas podem chegar em qualquer offset válido (ex.: {@code -03:00} ou {@code Z});
 * todas são convertidas para UTC com {@code withOffsetSameInstant(ZoneOffset.UTC)}, preservando o
 * instante exato e garantindo o cruzamento consistente com as janelas avaliadas pelo
 * {@link SlotService}. As respostas sempre retornam datas normalizadas em UTC.
 */
@Service
public class GestaoBloqueioService {

    public static final int TAMANHO_MAXIMO_MOTIVO = 120;

    private final BloqueioRepository bloqueioRepository;
    private final ProfissionalService profissionalService;
    private final AgendamentoConsultaService agendamentoConsultaService;

    public GestaoBloqueioService(BloqueioRepository bloqueioRepository,
                                 ProfissionalService profissionalService,
                                 AgendamentoConsultaService agendamentoConsultaService) {
        this.bloqueioRepository = bloqueioRepository;
        this.profissionalService = profissionalService;
        this.agendamentoConsultaService = agendamentoConsultaService;
    }

    @Transactional(readOnly = true)
    public List<BloqueioResponse> listarDoProfissional(UUID tenantId, UUID profissionalId,
                                                       OffsetDateTime de, OffsetDateTime ate) {
        profissionalService.buscarDoTenant(tenantId, profissionalId);

        OffsetDateTime deUtc = de != null ? de.withOffsetSameInstant(ZoneOffset.UTC) : null;
        OffsetDateTime ateUtc = ate != null ? ate.withOffsetSameInstant(ZoneOffset.UTC) : null;

        List<Bloqueio> bloqueios;
        if (deUtc != null && ateUtc != null) {
            if (!ateUtc.isAfter(deUtc)) {
                throw new RegraNegocioExcecao("A data final do período deve ser maior que a data inicial.");
            }
            bloqueios = bloqueioRepository.buscarInterceptando(tenantId, profissionalId, deUtc, ateUtc);
        } else if (deUtc != null) {
            bloqueios = bloqueioRepository.buscarAPartirDe(tenantId, profissionalId, deUtc);
        } else if (ateUtc != null) {
            bloqueios = bloqueioRepository.buscarAte(tenantId, profissionalId, ateUtc);
        } else {
            bloqueios = bloqueioRepository.findAllByTenantIdAndProfissionalIdOrderByInicioAsc(
                    tenantId, profissionalId);
        }

        return bloqueios.stream().map(BloqueioResponse::de).toList();
    }

    @Transactional
    public BloqueioResponse criar(UUID profissionalId, SalvarBloqueioRequest requisicao, UUID tenantId) {
        OffsetDateTime inicioUtc = normalizarUtc(requisicao.inicio());
        OffsetDateTime fimUtc = normalizarUtc(requisicao.fim());
        validarIntervalo(inicioUtc, fimUtc);
        String motivoNormalizado = validarENormalizarMotivo(requisicao.motivo());

        Profissional profissional = profissionalService.buscarDoTenant(tenantId, profissionalId);
        verificarConflitoAgendamentos(tenantId, profissionalId, inicioUtc, fimUtc,
                requisicao.confirmadoMesmoComConflito());

        // Sobreposição entre bloqueios do próprio profissional é permitida; apenas unida na exibição.
        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setTenantId(tenantId);
        bloqueio.setProfissional(profissional);
        bloqueio.setInicio(inicioUtc);
        bloqueio.setFim(fimUtc);
        bloqueio.setMotivo(motivoNormalizado);

        return BloqueioResponse.de(bloqueioRepository.save(bloqueio));
    }

    @Transactional
    public BloqueioResponse atualizar(UUID profissionalId, UUID blocoId,
                                      SalvarBloqueioRequest requisicao, UUID tenantId) {
        OffsetDateTime inicioUtc = normalizarUtc(requisicao.inicio());
        OffsetDateTime fimUtc = normalizarUtc(requisicao.fim());
        validarIntervalo(inicioUtc, fimUtc);
        String motivoNormalizado = validarENormalizarMotivo(requisicao.motivo());

        profissionalService.buscarDoTenant(tenantId, profissionalId);
        Bloqueio bloqueio = bloqueioRepository
                .findByIdAndTenantIdAndProfissionalId(blocoId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Bloqueio não encontrado."));

        verificarConflitoAgendamentos(tenantId, profissionalId, inicioUtc, fimUtc,
                requisicao.confirmadoMesmoComConflito());

        bloqueio.setInicio(inicioUtc);
        bloqueio.setFim(fimUtc);
        bloqueio.setMotivo(motivoNormalizado);

        return BloqueioResponse.de(bloqueioRepository.save(bloqueio));
    }

    @Transactional
    public void excluir(UUID profissionalId, UUID blocoId, UUID tenantId) {
        profissionalService.buscarDoTenant(tenantId, profissionalId);
        Bloqueio bloqueio = bloqueioRepository
                .findByIdAndTenantIdAndProfissionalId(blocoId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Bloqueio não encontrado."));
        bloqueioRepository.delete(bloqueio);
    }

    private void verificarConflitoAgendamentos(UUID tenantId, UUID profissionalId,
                                               OffsetDateTime inicioUtc, OffsetDateTime fimUtc,
                                               boolean confirmadoMesmoComConflito) {
        if (confirmadoMesmoComConflito) {
            return;
        }
        List<Agendamento> ocupados = agendamentoConsultaService.buscarOcupadosComServico(
                tenantId, profissionalId, inicioUtc, fimUtc);
        if (!ocupados.isEmpty()) {
            List<AgendamentoConflitanteResponse> conflitantes = ocupados.stream()
                    .map(AgendamentoConflitanteResponse::de)
                    .toList();
            throw new ConflitoBloqueioExcecao(conflitantes);
        }
    }

    private static OffsetDateTime normalizarUtc(OffsetDateTime dataHora) {
        return dataHora.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static void validarIntervalo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (!fim.isAfter(inicio)) {
            throw new RegraNegocioExcecao("A data e hora de fim deve ser maior que a de início.");
        }
    }

    private static String validarENormalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String limpo = motivo.trim();
        if (limpo.length() > TAMANHO_MAXIMO_MOTIVO) {
            throw new RegraNegocioExcecao(
                    "O motivo do bloqueio deve ter no máximo " + TAMANHO_MAXIMO_MOTIVO + " caracteres.");
        }
        return limpo;
    }
}
