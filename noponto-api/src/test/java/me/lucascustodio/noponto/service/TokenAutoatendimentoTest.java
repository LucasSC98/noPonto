package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.AgendamentoAutoatendimentoResponse;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.exception.MuitasTentativasExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.notificacao.EventoEmailAgendamento;
import me.lucascustodio.noponto.notificacao.TemplateEmailAgendamento;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.tenant.LimitadorTentativasAutoatendimento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenAutoatendimentoTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-20T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-22T14:00:00Z");

    private AgendamentoRepository agendamentoRepository;
    private ProfissionalService profissionalService;
    private SlotService slotService;
    private TenantService tenantService;
    private TokenAutoatendimentoService tokenService;
    private ApplicationEventPublisher eventPublisher;
    private AgendamentoService agendamentoService;
    private Profissional profissional;
    private ServicoItem servico;
    private Tenant tenant;

    @BeforeEach
    void configurar() {
        agendamentoRepository = mock(AgendamentoRepository.class);
        profissionalService = mock(ProfissionalService.class);
        slotService = mock(SlotService.class);
        tenantService = mock(TenantService.class);
        tokenService = new TokenAutoatendimentoService();
        eventPublisher = mock(ApplicationEventPublisher.class);
        Clock relogio = Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC);

        agendamentoService = new AgendamentoService(
                agendamentoRepository,
                profissionalService,
                slotService,
                tenantService,
                relogio,
                tokenService,
                new TemplateEmailAgendamento(),
                eventPublisher,
                "http://localhost:5173");

        profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setNome("Dr. João Silva");
        profissional.setEspecialidade("Clínico Geral");

        servico = new ServicoItem();
        servico.setId(SERVICO_ID);
        servico.setNome("Consulta Geral");
        servico.setDuracaoMinutos(30);

        tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setNome("Clínica Teste");
        tenant.setSubdominio("clinica-teste");
        tenant.setFusoHorario("America/Sao_Paulo");
        tenant.setAntecedenciaMinimaCancelamentoMinutos(120);

        when(profissionalService.buscarAtivoComBloqueio(TENANT_ID, PROFISSIONAL_ID)).thenReturn(profissional);
        when(profissionalService.buscarServicoParaAgendamento(TENANT_ID, PROFISSIONAL_ID, SERVICO_ID))
                .thenReturn(servico);
        when(tenantService.buscarPorId(TENANT_ID)).thenReturn(tenant);
        when(slotService.slotDisponivel(PROFISSIONAL_ID, INICIO, TENANT_ID, SERVICO_ID)).thenReturn(true);
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> {
            Agendamento a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
    }

    @Test
    void deve_persistirApenasHashSha256EDispararEmailComLinkPuro_quando_criarAgendamento() {
        AgendamentoService.AgendamentoCriadoComToken resultado = agendamentoService.criarComToken(
                new CriarAgendamentoRequest(
                        PROFISSIONAL_ID, SERVICO_ID, "Maria Silva", "maria@email.com", INICIO),
                TENANT_ID);

        ArgumentCaptor<Agendamento> agendamentoCaptor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(agendamentoCaptor.capture());
        Agendamento salvo = agendamentoCaptor.getValue();

        String tokenPuro = resultado.tokenPuro();
        assertThat(tokenPuro).hasSize(43);
        assertThat(salvo.getTokenGerenciamentoHash())
                .hasSize(64)
                .isNotEqualTo(tokenPuro)
                .isEqualTo(tokenService.calcularHash(tokenPuro));

        ArgumentCaptor<EventoEmailAgendamento> eventoCaptor =
                ArgumentCaptor.forClass(EventoEmailAgendamento.class);
        verify(eventPublisher).publishEvent(eventoCaptor.capture());
        EventoEmailAgendamento evento = eventoCaptor.getValue();
        assertThat(evento.mensagem().destinatario()).isEqualTo("maria@email.com");
        assertThat(evento.mensagem().corpoTexto())
                .contains("http://localhost:5173/agendamento/" + tokenPuro)
                .contains("Clínica Teste")
                .contains("Consulta Geral");
    }

    @Test
    void deve_retornar404Generico_quando_tokenInvalidoExpiradoCanceladoOuConcluido() {
        String tokenPuro = tokenService.gerar().tokenPuro();
        String hash = tokenService.calcularHash(tokenPuro);

        // 1. Inexistente
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> agendamentoService.consultarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Agendamento não encontrado ou link de autoatendimento inválido.");

        // 2. Expirado (fim <= agora)
        Agendamento expirado = criarAgendamento(AGORA.minusHours(1), AGORA, StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(expirado));
        assertThatThrownBy(() -> agendamentoService.consultarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Agendamento não encontrado ou link de autoatendimento inválido.");

        // 3. Cancelado
        Agendamento cancelado = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CANCELADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(cancelado));
        assertThatThrownBy(() -> agendamentoService.consultarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Agendamento não encontrado ou link de autoatendimento inválido.");

        // 4. Concluído
        Agendamento concluido = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONCLUIDO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(concluido));
        assertThatThrownBy(() -> agendamentoService.consultarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Agendamento não encontrado ou link de autoatendimento inválido.");
    }

    @Test
    void deve_retornarResumoDoAgendamento_quando_tokenValidoEAtivo() {
        String tokenPuro = tokenService.gerar().tokenPuro();
        String hash = tokenService.calcularHash(tokenPuro);
        Agendamento ativo = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(ativo));

        AgendamentoAutoatendimentoResponse resposta = agendamentoService.consultarPorToken(tokenPuro);

        assertThat(resposta.id()).isEqualTo(ativo.getId());
        assertThat(resposta.nomeEstabelecimento()).isEqualTo("Clínica Teste");
        assertThat(resposta.profissionalNome()).isEqualTo("Dr. João Silva");
        assertThat(resposta.servicoNome()).isEqualTo("Consulta Geral");
    }

    @Test
    void deve_bloquearCom429_quando_ipExcedeLimiteDeTentativasPorMinuto() {
        Clock relogio = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
        LimitadorTentativasAutoatendimento limitador = new LimitadorTentativasAutoatendimento(relogio, 3);

        limitador.verificarIp("203.0.113.10");
        limitador.verificarIp("203.0.113.10");
        limitador.verificarIp("203.0.113.10");

        assertThatThrownBy(() -> limitador.verificarIp("203.0.113.10"))
                .isInstanceOf(MuitasTentativasExcecao.class);
        // Outro IP não é bloqueado
        limitador.verificarIp("203.0.113.11");
    }

    private Agendamento criarAgendamento(OffsetDateTime inicio, OffsetDateTime fim, StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setTenantId(TENANT_ID);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente("Maria Silva");
        agendamento.setEmailCliente("maria@email.com");
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
        agendamento.setStatus(status);
        return agendamento;
    }
}
