package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.exception.HorarioIndisponivelExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgendamentoServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-20T12:00:00Z");

    private AgendamentoRepository agendamentoRepository;
    private ProfissionalService profissionalService;
    private SlotService slotService;
    private TenantService tenantService;
    private AgendamentoService agendamentoService;
    private Profissional profissional;
    private ServicoItem servico;

    @BeforeEach
    void configurar() {
        agendamentoRepository = mock(AgendamentoRepository.class);
        profissionalService = mock(ProfissionalService.class);
        slotService = mock(SlotService.class);
        tenantService = mock(TenantService.class);
        // agora = 18/11 12:00Z, bem antes do agendamento de 20/11 12:00Z
        Clock relogio = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);
        agendamentoService = new AgendamentoService(agendamentoRepository, profissionalService,
                slotService, tenantService, relogio);

        profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        servico = new ServicoItem();
        servico.setId(SERVICO_ID);
        servico.setDuracaoMinutos(45);
        when(profissionalService.buscarAtivoComBloqueio(TENANT_ID, PROFISSIONAL_ID)).thenReturn(profissional);
        when(profissionalService.buscarServicoAtivo(TENANT_ID, PROFISSIONAL_ID, SERVICO_ID)).thenReturn(servico);
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void deve_criarAgendamentoPendenteComFimCalculado_quando_slotDisponivel() {
        when(slotService.slotDisponivel(PROFISSIONAL_ID, INICIO, TENANT_ID, SERVICO_ID)).thenReturn(true);

        AgendamentoResponse resposta = agendamentoService.criar(new CriarAgendamentoRequest(
                PROFISSIONAL_ID, SERVICO_ID, " Maria ", "maria@email.com",
                OffsetDateTime.parse("2026-11-20T09:00:00-03:00")), TENANT_ID);

        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        Agendamento salvo = captor.getValue();
        assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(salvo.getNomeCliente()).isEqualTo("Maria");
        assertThat(salvo.getInicio().toInstant()).isEqualTo(INICIO.toInstant());
        assertThat(salvo.getFim().toInstant()).isEqualTo(Instant.parse("2026-11-20T12:45:00Z"));
        assertThat(salvo.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(resposta.status()).isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void deve_lancarExcecao_quando_horarioJaReservado() {
        when(slotService.slotDisponivel(PROFISSIONAL_ID, INICIO, TENANT_ID, SERVICO_ID)).thenReturn(false);

        assertThatThrownBy(() -> agendamentoService.criar(new CriarAgendamentoRequest(
                PROFISSIONAL_ID, SERVICO_ID, "Maria", "maria@email.com", INICIO), TENANT_ID))
                .isInstanceOf(HorarioIndisponivelExcecao.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void deve_lancarExcecao_quando_alterarAgendamentoConcluido() {
        Agendamento agendamento = agendamentoComStatus(StatusAgendamento.CONCLUIDO, INICIO);

        assertThatThrownBy(() -> agendamentoService.atualizarStatus(agendamento.getId(),
                StatusAgendamento.CANCELADO, TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessageContaining("concluído");
    }

    @Test
    void deve_lancarExcecao_quando_transicaoDeStatusInvalida() {
        Agendamento agendamento = agendamentoComStatus(StatusAgendamento.PENDENTE, INICIO);

        assertThatThrownBy(() -> agendamentoService.atualizarStatus(agendamento.getId(),
                StatusAgendamento.CONCLUIDO, TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class);
    }

    @Test
    void deve_cancelarERegistrarData_quando_respeitaAntecedenciaMinima() {
        Agendamento agendamento = agendamentoComStatus(StatusAgendamento.CONFIRMADO, INICIO);
        when(tenantService.buscarPorId(TENANT_ID)).thenReturn(tenantComAntecedencia(120));

        AgendamentoResponse resposta = agendamentoService.atualizarStatus(
                agendamento.getId(), StatusAgendamento.CANCELADO, TENANT_ID);

        assertThat(resposta.status()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getCanceladoEm()).isNotNull();
    }

    @Test
    void deve_lancarExcecao_quando_cancelarForaDaAntecedenciaMinima() {
        // agora = 18/11 12:00Z; agendamento em 18/11 13:00Z (1h) com antecedência mínima de 2h
        Agendamento agendamento = agendamentoComStatus(StatusAgendamento.CONFIRMADO,
                OffsetDateTime.parse("2026-11-18T13:00:00Z"));
        when(tenantService.buscarPorId(TENANT_ID)).thenReturn(tenantComAntecedencia(120));

        assertThatThrownBy(() -> agendamentoService.atualizarStatus(
                agendamento.getId(), StatusAgendamento.CANCELADO, TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessageContaining("antecedência");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    @Test
    void deve_confirmarAgendamento_quando_statusPendente() {
        Agendamento agendamento = agendamentoComStatus(StatusAgendamento.PENDENTE, INICIO);

        AgendamentoResponse resposta = agendamentoService.atualizarStatus(
                agendamento.getId(), StatusAgendamento.CONFIRMADO, TENANT_ID);

        assertThat(resposta.status()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    private Agendamento agendamentoComStatus(StatusAgendamento status, OffsetDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setTenantId(TENANT_ID);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente("Maria");
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plusMinutes(45));
        agendamento.setStatus(status);
        when(agendamentoRepository.findByIdAndTenantId(agendamento.getId(), TENANT_ID))
                .thenReturn(Optional.of(agendamento));
        return agendamento;
    }

    private static Tenant tenantComAntecedencia(int minutos) {
        Tenant tenant = new Tenant();
        tenant.setAntecedenciaMinimaCancelamentoMinutos(minutos);
        return tenant;
    }
}
