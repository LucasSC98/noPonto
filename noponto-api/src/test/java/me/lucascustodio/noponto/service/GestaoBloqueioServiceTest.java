package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.dto.AgendamentoConflitanteResponse;
import me.lucascustodio.noponto.dto.BloqueioResponse;
import me.lucascustodio.noponto.dto.SalvarBloqueioRequest;
import me.lucascustodio.noponto.exception.ConflitoBloqueioExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GestaoBloqueioServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID BLOCO_ID = UUID.randomUUID();

    private BloqueioRepository bloqueioRepository;
    private ProfissionalService profissionalService;
    private AgendamentoConsultaService agendamentoConsultaService;
    private GestaoBloqueioService service;
    private Profissional profissional;

    @BeforeEach
    void configurar() {
        bloqueioRepository = mock(BloqueioRepository.class);
        profissionalService = mock(ProfissionalService.class);
        agendamentoConsultaService = mock(AgendamentoConsultaService.class);
        service = new GestaoBloqueioService(
                bloqueioRepository, profissionalService, agendamentoConsultaService);

        profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setTenantId(TENANT_ID);
        profissional.setNome("Dr. João Silva");

        when(profissionalService.buscarDoTenant(TENANT_ID, PROFISSIONAL_ID)).thenReturn(profissional);
        when(agendamentoConsultaService.buscarOcupadosComServico(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(bloqueioRepository.save(any(Bloqueio.class))).thenAnswer(chamada -> {
            Bloqueio salvo = chamada.getArgument(0);
            if (salvo.getId() == null) {
                salvo.setId(UUID.randomUUID());
            }
            return salvo;
        });
    }

    @Test
    void deve_criarBloqueioNormalizandoParaUtc_quando_intervaloValidoComOffsetDeBrasilia() {
        SalvarBloqueioRequest requisicao = new SalvarBloqueioRequest(
                OffsetDateTime.parse("2026-12-10T09:00:00-03:00"),
                OffsetDateTime.parse("2026-12-10T12:00:00-03:00"),
                "  Congresso médico  ",
                null);

        BloqueioResponse resposta = service.criar(PROFISSIONAL_ID, requisicao, TENANT_ID);

        ArgumentCaptor<Bloqueio> captor = ArgumentCaptor.forClass(Bloqueio.class);
        verify(bloqueioRepository).save(captor.capture());
        Bloqueio salvo = captor.getValue();

        assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(salvo.getInicio()).isEqualTo(OffsetDateTime.parse("2026-12-10T12:00:00Z"));
        assertThat(salvo.getFim()).isEqualTo(OffsetDateTime.parse("2026-12-10T15:00:00Z"));
        assertThat(salvo.getInicio().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(salvo.getFim().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(salvo.getMotivo()).isEqualTo("Congresso médico");

        assertThat(resposta.profissionalId()).isEqualTo(PROFISSIONAL_ID);
        assertThat(resposta.inicio()).isEqualTo(OffsetDateTime.parse("2026-12-10T12:00:00Z"));
        assertThat(resposta.fim()).isEqualTo(OffsetDateTime.parse("2026-12-10T15:00:00Z"));
        assertThat(resposta.motivo()).isEqualTo("Congresso médico");
    }

    @Test
    void deve_permitirDiaInteiroESemMotivoESobreposicaoComOutrosBloqueios_quando_dadosValidos() {
        SalvarBloqueioRequest diaInteiro = new SalvarBloqueioRequest(
                OffsetDateTime.parse("2026-12-25T00:00:00-03:00"),
                OffsetDateTime.parse("2026-12-26T00:00:00-03:00"),
                "   ",
                false);

        BloqueioResponse resposta = service.criar(PROFISSIONAL_ID, diaInteiro, TENANT_ID);

        assertThat(resposta.motivo()).isNull();
        assertThat(resposta.inicio()).isEqualTo(OffsetDateTime.parse("2026-12-25T03:00:00Z"));
        assertThat(resposta.fim()).isEqualTo(OffsetDateTime.parse("2026-12-26T03:00:00Z"));
        verify(bloqueioRepository, never()).buscarInterceptando(any(), any(), any(), any());
    }

    @Test
    void deve_lancarRegraNegocioExcecao_quando_fimMenorOuIgualAoInicio() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-12-10T12:00:00Z");

        assertThatThrownBy(() -> service.criar(
                PROFISSIONAL_ID,
                new SalvarBloqueioRequest(instante, instante.minusHours(1), "Folga", false),
                TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("A data e hora de fim deve ser maior que a de início.");

        assertThatThrownBy(() -> service.criar(
                PROFISSIONAL_ID,
                new SalvarBloqueioRequest(instante, instante, "Folga", false),
                TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("A data e hora de fim deve ser maior que a de início.");

        verifyNoInteractions(bloqueioRepository, profissionalService, agendamentoConsultaService);
    }

    @Test
    void deve_lancarRegraNegocioExcecao_quando_motivoExcede120Caracteres() {
        String motivoLongo = "a".repeat(121);
        SalvarBloqueioRequest requisicao = new SalvarBloqueioRequest(
                OffsetDateTime.parse("2026-12-10T12:00:00Z"),
                OffsetDateTime.parse("2026-12-10T14:00:00Z"),
                motivoLongo,
                false);

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, requisicao, TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("O motivo do bloqueio deve ter no máximo 120 caracteres.");

        verifyNoInteractions(bloqueioRepository, profissionalService, agendamentoConsultaService);
    }

    @Test
    void deve_lancarRecursoNaoEncontrado_quando_profissionalPertenceAOutroTenant() {
        when(profissionalService.buscarDoTenant(OUTRO_TENANT_ID, PROFISSIONAL_ID))
                .thenThrow(new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        SalvarBloqueioRequest requisicao = new SalvarBloqueioRequest(
                OffsetDateTime.parse("2026-12-10T12:00:00Z"),
                OffsetDateTime.parse("2026-12-10T14:00:00Z"),
                "Folga",
                false);

        assertThatThrownBy(() -> service.listarDoProfissional(OUTRO_TENANT_ID, PROFISSIONAL_ID, null, null))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, requisicao, OUTRO_TENANT_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        assertThatThrownBy(() -> service.atualizar(PROFISSIONAL_ID, BLOCO_ID, requisicao, OUTRO_TENANT_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        assertThatThrownBy(() -> service.excluir(PROFISSIONAL_ID, BLOCO_ID, OUTRO_TENANT_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);

        verifyNoInteractions(bloqueioRepository, agendamentoConsultaService);
    }

    @Test
    void deve_lancarConflito409ComListaDeAgendamentos_quando_criarBloqueioSobrepondoAgendamentoAtivoSemConfirmacao() {
        OffsetDateTime inicioBloqueio = OffsetDateTime.parse("2026-12-10T12:00:00Z");
        OffsetDateTime fimBloqueio = OffsetDateTime.parse("2026-12-10T15:00:00Z");
        Agendamento agendamento = agendamentoAtivo(
                "Maria Oliveira",
                "Consulta Geral",
                OffsetDateTime.parse("2026-12-10T13:00:00Z"),
                OffsetDateTime.parse("2026-12-10T13:30:00Z"),
                StatusAgendamento.CONFIRMADO);

        when(agendamentoConsultaService.buscarOcupadosComServico(
                TENANT_ID, PROFISSIONAL_ID, inicioBloqueio, fimBloqueio))
                .thenReturn(List.of(agendamento));

        SalvarBloqueioRequest requisicao = new SalvarBloqueioRequest(
                inicioBloqueio, fimBloqueio, "Consulta externa", false);

        assertThatThrownBy(() -> service.criar(PROFISSIONAL_ID, requisicao, TENANT_ID))
                .isInstanceOf(ConflitoBloqueioExcecao.class)
                .satisfies(excecao -> {
                    ConflitoBloqueioExcecao conflito = (ConflitoBloqueioExcecao) excecao;
                    assertThat(conflito.getAgendamentosConflitantes()).containsExactly(
                            new AgendamentoConflitanteResponse(
                                    agendamento.getId(),
                                    "Maria Oliveira",
                                    "Consulta Geral",
                                    OffsetDateTime.parse("2026-12-10T13:00:00Z"),
                                    OffsetDateTime.parse("2026-12-10T13:30:00Z"),
                                    StatusAgendamento.CONFIRMADO));
                });

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void deve_criarBloqueioMantendoAgendamentos_quando_confirmarMesmoComConflitoVerdadeiro() {
        OffsetDateTime inicioBloqueio = OffsetDateTime.parse("2026-12-10T12:00:00Z");
        OffsetDateTime fimBloqueio = OffsetDateTime.parse("2026-12-10T15:00:00Z");
        Agendamento agendamento = agendamentoAtivo(
                "Carlos Souza",
                "Retorno",
                OffsetDateTime.parse("2026-12-10T13:00:00Z"),
                OffsetDateTime.parse("2026-12-10T13:15:00Z"),
                StatusAgendamento.PENDENTE);

        when(agendamentoConsultaService.buscarOcupadosComServico(
                TENANT_ID, PROFISSIONAL_ID, inicioBloqueio, fimBloqueio))
                .thenReturn(List.of(agendamento));

        BloqueioResponse resposta = service.criar(
                PROFISSIONAL_ID,
                new SalvarBloqueioRequest(inicioBloqueio, fimBloqueio, "Emergência", true),
                TENANT_ID);

        assertThat(resposta.id()).isNotNull();
        assertThat(resposta.motivo()).isEqualTo("Emergência");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
        verify(bloqueioRepository).save(any(Bloqueio.class));
        verifyNoInteractions(agendamentoConsultaService);
    }

    @Test
    void deve_lancarConflito409EConfirmarEdicao_quando_atualizarBloqueioSobrepondoAgendamento() {
        Bloqueio existente = new Bloqueio();
        existente.setId(BLOCO_ID);
        existente.setTenantId(TENANT_ID);
        existente.setProfissional(profissional);
        existente.setInicio(OffsetDateTime.parse("2026-12-10T08:00:00Z"));
        existente.setFim(OffsetDateTime.parse("2026-12-10T09:00:00Z"));
        when(bloqueioRepository.findByIdAndTenantIdAndProfissionalId(BLOCO_ID, TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(Optional.of(existente));

        OffsetDateTime novoInicio = OffsetDateTime.parse("2026-12-10T12:00:00Z");
        OffsetDateTime novoFim = OffsetDateTime.parse("2026-12-10T14:00:00Z");
        Agendamento agendamento = agendamentoAtivo(
                "Ana Paula",
                "Consulta Geral",
                OffsetDateTime.parse("2026-12-10T13:00:00Z"),
                OffsetDateTime.parse("2026-12-10T13:30:00Z"),
                StatusAgendamento.CONFIRMADO);
        when(agendamentoConsultaService.buscarOcupadosComServico(TENANT_ID, PROFISSIONAL_ID, novoInicio, novoFim))
                .thenReturn(List.of(agendamento));

        assertThatThrownBy(() -> service.atualizar(
                PROFISSIONAL_ID,
                BLOCO_ID,
                new SalvarBloqueioRequest(novoInicio, novoFim, "Reunião", false),
                TENANT_ID))
                .isInstanceOf(ConflitoBloqueioExcecao.class);

        BloqueioResponse atualizado = service.atualizar(
                PROFISSIONAL_ID,
                BLOCO_ID,
                new SalvarBloqueioRequest(novoInicio, novoFim, "Reunião", true),
                TENANT_ID);

        assertThat(atualizado.inicio()).isEqualTo(novoInicio);
        assertThat(atualizado.fim()).isEqualTo(novoFim);
        assertThat(atualizado.motivo()).isEqualTo("Reunião");
    }

    @Test
    void deve_filtrarBloqueiosPorPeriodo_quando_deOuAteInformados() {
        OffsetDateTime de = OffsetDateTime.parse("2026-12-01T00:00:00-03:00");
        OffsetDateTime ate = OffsetDateTime.parse("2026-12-31T23:59:59-03:00");
        OffsetDateTime deUtc = de.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime ateUtc = ate.withOffsetSameInstant(ZoneOffset.UTC);

        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setId(BLOCO_ID);
        bloqueio.setProfissional(profissional);
        bloqueio.setInicio(OffsetDateTime.parse("2026-12-25T03:00:00Z"));
        bloqueio.setFim(OffsetDateTime.parse("2026-12-26T03:00:00Z"));
        bloqueio.setMotivo("Natal");

        when(bloqueioRepository.buscarInterceptando(TENANT_ID, PROFISSIONAL_ID, deUtc, ateUtc))
                .thenReturn(List.of(bloqueio));
        when(bloqueioRepository.buscarAPartirDe(TENANT_ID, PROFISSIONAL_ID, deUtc))
                .thenReturn(List.of(bloqueio));
        when(bloqueioRepository.buscarAte(TENANT_ID, PROFISSIONAL_ID, ateUtc))
                .thenReturn(List.of(bloqueio));
        when(bloqueioRepository.findAllByTenantIdAndProfissionalIdOrderByInicioAsc(TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(List.of(bloqueio));

        assertThat(service.listarDoProfissional(TENANT_ID, PROFISSIONAL_ID, de, ate)).hasSize(1);
        assertThat(service.listarDoProfissional(TENANT_ID, PROFISSIONAL_ID, de, null)).hasSize(1);
        assertThat(service.listarDoProfissional(TENANT_ID, PROFISSIONAL_ID, null, ate)).hasSize(1);
        assertThat(service.listarDoProfissional(TENANT_ID, PROFISSIONAL_ID, null, null)).hasSize(1);

        assertThatThrownBy(() -> service.listarDoProfissional(TENANT_ID, PROFISSIONAL_ID, ate, de))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("A data final do período deve ser maior que a data inicial.");
    }

    @Test
    void deve_excluirBloqueio_quando_pertenceAoProfissionalETenant() {
        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setId(BLOCO_ID);
        bloqueio.setTenantId(TENANT_ID);
        bloqueio.setProfissional(profissional);
        when(bloqueioRepository.findByIdAndTenantIdAndProfissionalId(BLOCO_ID, TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(Optional.of(bloqueio));

        service.excluir(PROFISSIONAL_ID, BLOCO_ID, TENANT_ID);

        verify(bloqueioRepository).delete(bloqueio);
    }

    private Agendamento agendamentoAtivo(String nomeCliente,
                                         String servicoNome,
                                         OffsetDateTime inicio,
                                         OffsetDateTime fim,
                                         StatusAgendamento status) {
        ServicoItem servico = new ServicoItem();
        servico.setId(UUID.randomUUID());
        servico.setNome(servicoNome);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setTenantId(TENANT_ID);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente(nomeCliente);
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
        agendamento.setStatus(status);
        return agendamento;
    }
}
