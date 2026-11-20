package me.lucascustodio.noponto.notificacao;

import me.lucascustodio.noponto.PostgresTestcontainersConfig;
import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import me.lucascustodio.noponto.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class LembreteAgendamentoTest {

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProfissionalRepository profissionalRepository;

    @Autowired
    private ServicoItemRepository servicoItemRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TemplateEmailAgendamento templateEmailAgendamento;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private EnviadorEmail enviadorEmail;
    private Clock relogioFixo;
    private AgendadorLembreteAgendamento agendador;

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-20T12:00:00Z");

    @BeforeEach
    void configurar() {
        enviadorEmail = mock(EnviadorEmail.class);
        relogioFixo = Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC);
        agendador = new AgendadorLembreteAgendamento(
                agendamentoRepository,
                tenantService,
                templateEmailAgendamento,
                enviadorEmail,
                transactionTemplate,
                relogioFixo);
    }

    @Test
    void deve_enviarUmaUnicaVezEIgnorarCanceladosEForaDaJanela_quando_jobExecutadoDuasVezes() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant();
        tenant.setNome("Clínica Lembrete " + sufixo);
        tenant.setSubdominio("lembrete-" + sufixo);
        tenant.setAtivo(true);
        tenant = tenantRepository.save(tenant);

        Profissional profissional = new Profissional();
        profissional.setTenantId(tenant.getId());
        profissional.setNome("Dra. Helena");
        profissional.setAtivo(true);
        profissional = profissionalRepository.save(profissional);

        ServicoItem servico = new ServicoItem();
        servico.setTenantId(tenant.getId());
        servico.setProfissional(profissional);
        servico.setNome("Consulta 30min");
        servico.setDuracaoMinutos(30);
        servico.setAtivo(true);
        servico = servicoItemRepository.save(servico);

        // 1. Dentro da janela de ~24h e CONFIRMADO -> deve enviar 1 vez
        Agendamento elegivel = criarAgendamento(
                tenant.getId(), profissional, servico,
                "Cliente Elegível", "elegivel-" + sufixo + "@teste.com",
                AGORA.plusHours(24), StatusAgendamento.CONFIRMADO);

        // 2. Dentro da janela de ~24h mas CANCELADO -> deve ignorar
        criarAgendamento(
                tenant.getId(), profissional, servico,
                "Cliente Cancelado", "cancelado-" + sufixo + "@teste.com",
                AGORA.plusHours(24), StatusAgendamento.CANCELADO);

        // 3. Fora da janela (daqui a 5 horas) -> deve ignorar
        criarAgendamento(
                tenant.getId(), profissional, servico,
                "Cliente Cedo", "cedo-" + sufixo + "@teste.com",
                AGORA.plusHours(5), StatusAgendamento.PENDENTE);

        // 4. Fora da janela (daqui a 48 horas) -> deve ignorar
        criarAgendamento(
                tenant.getId(), profissional, servico,
                "Cliente Distante", "distante-" + sufixo + "@teste.com",
                AGORA.plusHours(48), StatusAgendamento.CONFIRMADO);

        // Primeira execução: reserva atomicamente e envia para o agendamento elegível
        int enviadosPrimeiraExecucao = agendador.processarLembretes24h();
        // Segunda execução: lembrete_enviado_em já está preenchido, não envia novamente
        int enviadosSegundaExecucao = agendador.processarLembretes24h();

        assertThat(enviadosPrimeiraExecucao).isGreaterThanOrEqualTo(1);
        assertThat(enviadosSegundaExecucao).isZero();

        ArgumentCaptor<MensagemEmail> mensagemCaptor = ArgumentCaptor.forClass(MensagemEmail.class);
        verify(enviadorEmail, times(enviadosPrimeiraExecucao)).enviar(mensagemCaptor.capture());

        List<MensagemEmail> mensagensDoTenant = mensagemCaptor.getAllValues().stream()
                .filter(m -> elegivel.getId().equals(m.agendamentoId()))
                .toList();
        assertThat(mensagensDoTenant).hasSize(1);
        assertThat(mensagensDoTenant.getFirst().destinatario())
                .isEqualTo("elegivel-" + sufixo + "@teste.com");

        Agendamento atualizadoNoBanco = agendamentoRepository
                .findByIdAndTenantId(elegivel.getId(), tenant.getId())
                .orElseThrow();
        assertThat(atualizadoNoBanco.getLembreteEnviadoEm()).isNotNull();
    }

    private Agendamento criarAgendamento(UUID tenantId,
                                         Profissional profissional,
                                         ServicoItem servico,
                                         String nomeCliente,
                                         String emailCliente,
                                         OffsetDateTime inicio,
                                         StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente(nomeCliente);
        agendamento.setEmailCliente(emailCliente);
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plusMinutes(30));
        agendamento.setStatus(status);
        agendamento.setCanceladoPeloCliente(false);
        agendamento.setRemarcado(false);
        return agendamentoRepository.save(agendamento);
    }
}
