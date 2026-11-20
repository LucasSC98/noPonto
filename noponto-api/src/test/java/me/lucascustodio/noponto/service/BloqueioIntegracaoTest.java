package me.lucascustodio.noponto.service;

import jakarta.persistence.EntityManager;
import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.dto.BloqueioResponse;
import me.lucascustodio.noponto.dto.SalvarBloqueioRequest;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class BloqueioIntegracaoTest {

    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProfissionalRepository profissionalRepository;

    @Autowired
    private ServicoItemRepository servicoItemRepository;

    @Autowired
    private RegraDisponibilidadeRepository regraDisponibilidadeRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private BloqueioRepository bloqueioRepository;

    @Autowired
    private GestaoBloqueioService gestaoBloqueioService;

    @Autowired
    private SlotService slotService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Clock relogio;

    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void deve_retornar409ComListaDeAgendamentosAtivosEConfirmarAposFlag_quando_criarBloqueioViaHttp() throws Exception {
        Tenant tenant = criarTenant("blq-conflito");
        Profissional profissional = criarProfissional(tenant.getId(), "Dr. João Silva");
        ServicoItem servico = criarServico(tenant.getId(), profissional, "Consulta Geral", 30);
        String token = tokenAdmin(tenant.getId());

        LocalDate segundaFutura = proximaSegundaFutura();
        OffsetDateTime inicioAgendamento = OffsetDateTime.of(segundaFutura, OffsetTime.of(10, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA);
        Agendamento confirmado = criarAgendamento(
                tenant.getId(), profissional, servico, "Maria Clara", inicioAgendamento, StatusAgendamento.CONFIRMADO);
        Agendamento cancelado = criarAgendamento(
                tenant.getId(), profissional, servico, "Pedro Cancelado", inicioAgendamento.plusHours(1), StatusAgendamento.CANCELADO);

        entityManager.flush();
        entityManager.clear();

        String inicioBloqueio = OffsetDateTime.of(segundaFutura, OffsetTime.of(9, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA).toString();
        String fimBloqueio = OffsetDateTime.of(segundaFutura, OffsetTime.of(12, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA).toString();

        String payloadSemConfirmacao = """
                {
                  "inicio": "%s",
                  "fim": "%s",
                  "motivo": "Treinamento"
                }
                """.formatted(inicioBloqueio, fimBloqueio);

        mockMvc.perform(post("/professionals/{id}/blocks", profissional.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadSemConfirmacao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.agendamentosConflitantes.length()").value(1))
                .andExpect(jsonPath("$.agendamentosConflitantes[0].id").value(confirmado.getId().toString()))
                .andExpect(jsonPath("$.agendamentosConflitantes[0].nomeCliente").value("Maria Clara"))
                .andExpect(jsonPath("$.agendamentosConflitantes[0].servicoNome").value("Consulta Geral"))
                .andExpect(jsonPath("$.agendamentosConflitantes[0].status").value("CONFIRMADO"));

        String payloadComConfirmacao = """
                {
                  "inicio": "%s",
                  "fim": "%s",
                  "motivo": "Treinamento",
                  "confirmarMesmoComConflito": true
                }
                """.formatted(inicioBloqueio, fimBloqueio);

        mockMvc.perform(post("/professionals/{id}/blocks", profissional.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComConfirmacao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profissionalId").value(profissional.getId().toString()))
                .andExpect(jsonPath("$.motivo").value("Treinamento"));

        entityManager.flush();
        entityManager.clear();

        Agendamento agendamentoNoBanco = agendamentoRepository
                .findByIdAndTenantId(confirmado.getId(), tenant.getId())
                .orElseThrow();
        assertThat(agendamentoNoBanco.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(cancelado.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void deve_isolarBloqueiosPorTenant_quando_usuarioDeOutroTenantAcessaProfissional() throws Exception {
        Tenant tenantA = criarTenant("blq-tenant-a");
        Tenant tenantB = criarTenant("blq-tenant-b");
        Profissional profA = criarProfissional(tenantA.getId(), "Dr. A");

        Bloqueio bloqueioA = new Bloqueio();
        bloqueioA.setTenantId(tenantA.getId());
        bloqueioA.setProfissional(profA);
        bloqueioA.setInicio(OffsetDateTime.parse("2026-12-20T12:00:00Z"));
        bloqueioA.setFim(OffsetDateTime.parse("2026-12-20T15:00:00Z"));
        bloqueioA.setMotivo("Férias");
        bloqueioA = bloqueioRepository.save(bloqueioA);

        entityManager.flush();
        entityManager.clear();

        String tokenTenantB = tokenAdmin(tenantB.getId());
        String payload = """
                {
                  "inicio": "2026-12-21T12:00:00Z",
                  "fim": "2026-12-21T14:00:00Z",
                  "motivo": "Teste"
                }
                """;

        mockMvc.perform(get("/professionals/{id}/blocks", profA.getId())
                        .header("Authorization", "Bearer " + tokenTenantB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/professionals/{id}/blocks", profA.getId())
                        .header("Authorization", "Bearer " + tokenTenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/professionals/{id}/blocks/{blocoId}", profA.getId(), bloqueioA.getId())
                        .header("Authorization", "Bearer " + tokenTenantB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/professionals/{id}/blocks/{blocoId}", profA.getId(), bloqueioA.getId())
                        .header("Authorization", "Bearer " + tokenTenantB))
                .andExpect(status().isNotFound());
    }

    @Test
    void deve_removerHorariosDoPeriodoBloqueado_quando_slotServiceCalculaDisponibilidade() {
        Tenant tenant = criarTenant("blq-slots");
        Profissional profissional = criarProfissional(tenant.getId(), "Dr. Slots");
        ServicoItem servico = criarServico(tenant.getId(), profissional, "Consulta 1h", 60);

        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setTenantId(tenant.getId());
        regra.setProfissional(profissional);
        regra.setDiaSemana(DayOfWeek.MONDAY);
        regra.setHoraInicio(OffsetTime.of(9, 0, 0, 0, BRASILIA));
        regra.setHoraFim(OffsetTime.of(13, 0, 0, 0, BRASILIA));
        regra.setAtivo(true);
        regraDisponibilidadeRepository.save(regra);

        LocalDate segundaFutura = proximaSegundaFutura();
        BloqueioResponse criado = gestaoBloqueioService.criar(
                profissional.getId(),
                new SalvarBloqueioRequest(
                        OffsetDateTime.of(segundaFutura, OffsetTime.of(10, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        OffsetDateTime.of(segundaFutura, OffsetTime.of(12, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        "Reunião clínica",
                        false),
                tenant.getId());
        assertThat(criado.id()).isNotNull();

        entityManager.flush();
        entityManager.clear();

        List<OffsetDateTime> slots = slotService.calcularSlotsDisponiveis(
                profissional.getId(), segundaFutura, tenant.getId(), servico.getId());

        OffsetDateTime slot09h = OffsetDateTime.of(segundaFutura, OffsetTime.of(9, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA)
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime slot12h = OffsetDateTime.of(segundaFutura, OffsetTime.of(12, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA)
                .withOffsetSameInstant(ZoneOffset.UTC);

        assertThat(slots).containsExactly(slot09h, slot12h);
    }

    @Test
    void deve_filtrarPorPeriodoDeEAte_quando_listarBloqueiosViaHttp() throws Exception {
        Tenant tenant = criarTenant("blq-filtro");
        Profissional profissional = criarProfissional(tenant.getId(), "Dra. Filtro");
        String token = tokenAdmin(tenant.getId());

        LocalDate segunda = proximaSegundaFutura();
        gestaoBloqueioService.criar(
                profissional.getId(),
                new SalvarBloqueioRequest(
                        OffsetDateTime.of(segunda, OffsetTime.of(9, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        OffsetDateTime.of(segunda, OffsetTime.of(11, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        "Primeiro",
                        false),
                tenant.getId());
        gestaoBloqueioService.criar(
                profissional.getId(),
                new SalvarBloqueioRequest(
                        OffsetDateTime.of(segunda.plusDays(10), OffsetTime.of(9, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        OffsetDateTime.of(segunda.plusDays(10), OffsetTime.of(11, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA),
                        "Segundo",
                        false),
                tenant.getId());

        entityManager.flush();
        entityManager.clear();

        String de = OffsetDateTime.of(segunda.plusDays(5), OffsetTime.of(0, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA).toString();
        String ate = OffsetDateTime.of(segunda.plusDays(15), OffsetTime.of(0, 0, 0, 0, BRASILIA).toLocalTime(), BRASILIA).toString();

        mockMvc.perform(get("/professionals/{id}/blocks", profissional.getId())
                        .param("de", de)
                        .param("ate", ate)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].motivo").value("Segundo"));
    }

    private LocalDate proximaSegundaFutura() {
        return LocalDate.now(relogio.withZone(BRASILIA))
                .plusDays(7)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private String tokenAdmin(UUID tenantId) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenantId);
        usuario.setEmail("admin-" + tenantId + "@clinica.com");
        usuario.setRole(PerfilUsuario.ADMIN);
        return jwtService.gerarToken(usuario);
    }

    private Tenant criarTenant(String prefixo) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = new Tenant();
        tenant.setNome("Clínica " + prefixo);
        tenant.setSubdominio(prefixo + "-" + sufixo);
        tenant.setAtivo(true);
        return tenantRepository.save(tenant);
    }

    private Profissional criarProfissional(UUID tenantId, String nome) {
        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setNome(nome);
        profissional.setAtivo(true);
        return profissionalRepository.save(profissional);
    }

    private ServicoItem criarServico(UUID tenantId, Profissional profissional, String nome, int duracaoMinutos) {
        ServicoItem servico = new ServicoItem();
        servico.setTenantId(tenantId);
        servico.setProfissional(profissional);
        servico.setNome(nome);
        servico.setDuracaoMinutos(duracaoMinutos);
        servico.setAtivo(true);
        return servicoItemRepository.save(servico);
    }

    private Agendamento criarAgendamento(UUID tenantId,
                                         Profissional profissional,
                                         ServicoItem servico,
                                         String nomeCliente,
                                         OffsetDateTime inicio,
                                         StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente(nomeCliente);
        agendamento.setEmailCliente("cliente@teste.com");
        agendamento.setInicio(inicio.withOffsetSameInstant(ZoneOffset.UTC));
        agendamento.setFim(inicio.plusMinutes(servico.getDuracaoMinutos()).withOffsetSameInstant(ZoneOffset.UTC));
        agendamento.setStatus(status);
        return agendamentoRepository.save(agendamento);
    }
}
