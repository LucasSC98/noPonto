package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.AgendamentoAutoatendimentoResponse;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.dto.RemarcarAgendamentoRequest;
import me.lucascustodio.noponto.exception.HorarioIndisponivelExcecao;
import me.lucascustodio.noponto.exception.MuitasTentativasExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenAutoatendimentoTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-20T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-22T14:00:00Z");
    private static final OffsetDateTime NOVO_INICIO = OffsetDateTime.parse("2026-11-23T15:00:00Z");

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
        profissional.setTelefone("(11) 99999-0000");

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
        assertThatThrownBy(() -> agendamentoService.cancelarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);

        // 4. Concluído
        Agendamento concluido = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONCLUIDO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(concluido));
        assertThatThrownBy(() -> agendamentoService.consultarPorToken(tokenPuro))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Agendamento não encontrado ou link de autoatendimento inválido.");
    }

    @Test
    void deve_cancelarDentroDoPrazoEMarcarOrigemClienteEDispararEmail_quando_tokenValido() {
        String tokenPuro = tokenService.gerar().tokenPuro();
        String hash = tokenService.calcularHash(tokenPuro);
        Agendamento agendamento = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(agendamento));

        // Mesmo que profissional/serviço fossem desativados, o cancelamento não exige profissional ativo
        when(profissionalService.buscarAtivoComBloqueio(any(), any()))
                .thenThrow(new RegraNegocioExcecao("Profissional inativo"));

        AgendamentoAutoatendimentoResponse resposta = agendamentoService.cancelarPorToken(tokenPuro);

        assertThat(resposta.status()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getCanceladoEm()).isEqualTo(AGORA);
        assertThat(agendamento.getCanceladoPeloCliente()).isTrue();
        verify(eventPublisher).publishEvent(any(EventoEmailAgendamento.class));
    }

    @Test
    void deve_lancarRegraNegocioExcecaoComPrazoEContato_quando_cancelarForaDaAntecedenciaMinima() {
        String tokenPuro = tokenService.gerar().tokenPuro();
        String hash = tokenService.calcularHash(tokenPuro);
        // Faltam apenas 60 minutos (prazo mínimo = 120 minutos)
        Agendamento agendamento = criarAgendamento(
                AGORA.plusMinutes(60), AGORA.plusMinutes(90), StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> agendamentoService.cancelarPorToken(tokenPuro))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessageContaining("antecedência mínima de 120 minutos")
                .hasMessageContaining("Clínica Teste")
                .hasMessageContaining("(11) 99999-0000");

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void deve_remarcarMantendoIdETokenEMarcarIndicadorRemarcado_quando_novoSlotDisponivel() {
        TokenAutoatendimentoService.TokenGerado tokenGerado = tokenService.gerar();
        Agendamento agendamento = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONFIRMADO);
        agendamento.setTokenGerenciamentoHash(tokenGerado.hashSha256());
        UUID idOriginal = agendamento.getId();

        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(tokenGerado.hashSha256()))
                .thenReturn(Optional.of(agendamento));
        when(slotService.slotDisponivel(PROFISSIONAL_ID, NOVO_INICIO, TENANT_ID, SERVICO_ID, idOriginal))
                .thenReturn(true);

        AgendamentoAutoatendimentoResponse resposta = agendamentoService.remarcarPorToken(
                tokenGerado.tokenPuro(), new RemarcarAgendamentoRequest(NOVO_INICIO));

        assertThat(resposta.id()).isEqualTo(idOriginal);
        assertThat(resposta.inicio()).isEqualTo(NOVO_INICIO);
        assertThat(resposta.fim()).isEqualTo(NOVO_INICIO.plusMinutes(30));
        assertThat(resposta.remarcado()).isTrue();
        assertThat(agendamento.getTokenGerenciamentoHash()).isEqualTo(tokenGerado.hashSha256());
        assertThat(agendamento.getRemarcado()).isTrue();
        verify(profissionalService).buscarAtivoComBloqueio(TENANT_ID, PROFISSIONAL_ID);
        verify(eventPublisher).publishEvent(any(EventoEmailAgendamento.class));
    }

    @Test
    void deve_manterHorarioAntigoELancar409_quando_remarcarParaHorarioOcupado() {
        TokenAutoatendimentoService.TokenGerado tokenGerado = tokenService.gerar();
        Agendamento agendamento = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(tokenGerado.hashSha256()))
                .thenReturn(Optional.of(agendamento));
        when(slotService.slotDisponivel(PROFISSIONAL_ID, NOVO_INICIO, TENANT_ID, SERVICO_ID, agendamento.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> agendamentoService.remarcarPorToken(
                tokenGerado.tokenPuro(), new RemarcarAgendamentoRequest(NOVO_INICIO)))
                .isInstanceOf(HorarioIndisponivelExcecao.class);

        assertThat(agendamento.getInicio()).isEqualTo(INICIO);
        assertThat(agendamento.getFim()).isEqualTo(INICIO.plusMinutes(30));
        assertThat(agendamento.getRemarcado()).isFalse();
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void deve_recusarRemarcacaoCom422_quando_profissionalOuServicoDesativado() {
        TokenAutoatendimentoService.TokenGerado tokenGerado = tokenService.gerar();
        Agendamento agendamento = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(tokenGerado.hashSha256()))
                .thenReturn(Optional.of(agendamento));
        when(profissionalService.buscarAtivoComBloqueio(TENANT_ID, PROFISSIONAL_ID))
                .thenThrow(new RegraNegocioExcecao("Profissional inativo não aceita novos agendamentos."));

        assertThatThrownBy(() -> agendamentoService.remarcarPorToken(
                tokenGerado.tokenPuro(), new RemarcarAgendamentoRequest(NOVO_INICIO)))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessageContaining("Profissional inativo");

        assertThat(agendamento.getInicio()).isEqualTo(INICIO);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void deve_isolarTenantDoProprioAgendamento_quando_remarcarPorToken() {
        UUID outroTenantId = UUID.randomUUID();
        TokenAutoatendimentoService.TokenGerado tokenGerado = tokenService.gerar();
        Agendamento agendamentoOutroTenant = criarAgendamento(INICIO, INICIO.plusMinutes(30), StatusAgendamento.PENDENTE);
        agendamentoOutroTenant.setTenantId(outroTenantId);

        Tenant outroTenant = new Tenant();
        outroTenant.setId(outroTenantId);
        outroTenant.setNome("Outra Clínica");
        outroTenant.setAntecedenciaMinimaCancelamentoMinutos(60);

        when(agendamentoRepository.buscarPorTokenGerenciamentoHash(tokenGerado.hashSha256()))
                .thenReturn(Optional.of(agendamentoOutroTenant));
        when(tenantService.buscarPorId(outroTenantId)).thenReturn(outroTenant);
        when(profissionalService.buscarAtivoComBloqueio(outroTenantId, PROFISSIONAL_ID)).thenReturn(profissional);
        when(profissionalService.buscarServicoParaAgendamento(outroTenantId, PROFISSIONAL_ID, SERVICO_ID))
                .thenReturn(servico);
        when(slotService.slotDisponivel(
                PROFISSIONAL_ID, NOVO_INICIO, outroTenantId, SERVICO_ID, agendamentoOutroTenant.getId()))
                .thenReturn(true);

        AgendamentoAutoatendimentoResponse resposta = agendamentoService.remarcarPorToken(
                tokenGerado.tokenPuro(), new RemarcarAgendamentoRequest(NOVO_INICIO));

        assertThat(resposta.nomeEstabelecimento()).isEqualTo("Outra Clínica");
        verify(profissionalService).buscarAtivoComBloqueio(outroTenantId, PROFISSIONAL_ID);
        verify(slotService).slotDisponivel(
                PROFISSIONAL_ID, NOVO_INICIO, outroTenantId, SERVICO_ID, agendamentoOutroTenant.getId());
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
        agendamento.setCanceladoPeloCliente(Boolean.FALSE);
        agendamento.setRemarcado(Boolean.FALSE);
        return agendamento;
    }
}
