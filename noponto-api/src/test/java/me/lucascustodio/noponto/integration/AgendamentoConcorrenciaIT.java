package me.lucascustodio.noponto.integration;

import lombok.extern.slf4j.Slf4j;
import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.exception.HorarioIndisponivelExcecao;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import me.lucascustodio.noponto.service.AgendamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AgendamentoConcorrenciaIT {

    private static final int TIMEOUT_SEGUNDOS = 30;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private AgendamentoService agendamentoService;

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

    @Test
    void deve_confirmarApenasUmAgendamento_quando_vinteThreadsDisputamMesmoHorario() throws InterruptedException {
        CenarioBase cenario = criarCenarioBase("mesmo-horario");
        OffsetDateTime horario = OffsetDateTime.of(cenario.data(), LocalTime.of(9, 0), ZoneOffset.UTC);

        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int indice = i;
            tarefas.add(() -> agendamentoService.criar(
                    new CriarAgendamentoRequest(
                            cenario.profissionalId(),
                            cenario.servico30MinId(),
                            "Cliente " + indice,
                            "cliente" + indice + "@teste.com",
                            horario),
                    cenario.tenantId()));
        }

        ResultadoConcorrencia resultado = executarConcorrente(tarefas);
        List<Agendamento> salvos = agendamentoRepository.findAllByTenantIdOrderByInicioAsc(cenario.tenantId());

        log.info("Cenário A (mesmo horário) -> threads={}, sucessos={}, conflitos={}, errosInesperados={}, salvosNoBanco={}",
                tarefas.size(), resultado.sucessos(), resultado.conflitosHorario(),
                resultado.errosInesperados().size(), salvos.size());

        assertThat(resultado.errosInesperados()).isEmpty();
        assertThat(resultado.sucessos()).isEqualTo(1);
        assertThat(resultado.conflitosHorario()).isEqualTo(19);
        assertThat(salvos).hasSize(1);
        assertThat(salvos.getFirst().getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void deve_confirmarApenasUmAgendamento_quando_vinteThreadsDisputamHorariosSobrepostos() throws InterruptedException {
        CenarioBase cenario = criarCenarioBase("sobrepostos");
        OffsetDateTime horario0900 = OffsetDateTime.of(cenario.data(), LocalTime.of(9, 0), ZoneOffset.UTC);
        OffsetDateTime horario0915 = OffsetDateTime.of(cenario.data(), LocalTime.of(9, 15), ZoneOffset.UTC);

        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int indice = i;
            // Serviço de 30 min às 09:00 -> [09:00, 09:30)
            tarefas.add(() -> agendamentoService.criar(
                    new CriarAgendamentoRequest(
                            cenario.profissionalId(),
                            cenario.servico30MinId(),
                            "Cliente 09:00 #" + indice,
                            "c0900_" + indice + "@teste.com",
                            horario0900),
                    cenario.tenantId()));
            // Serviço de 15 min às 09:15 -> [09:15, 09:30) (sobrepõe [09:00, 09:30))
            tarefas.add(() -> agendamentoService.criar(
                    new CriarAgendamentoRequest(
                            cenario.profissionalId(),
                            cenario.servico15MinId(),
                            "Cliente 09:15 #" + indice,
                            "c0915_" + indice + "@teste.com",
                            horario0915),
                    cenario.tenantId()));
        }

        ResultadoConcorrencia resultado = executarConcorrente(tarefas);
        List<Agendamento> salvos = agendamentoRepository.findAllByTenantIdOrderByInicioAsc(cenario.tenantId());

        log.info("Cenário B (horários sobrepostos 09:00 vs 09:15) -> threads={}, sucessos={}, conflitos={}, errosInesperados={}, salvosNoBanco={}",
                tarefas.size(), resultado.sucessos(), resultado.conflitosHorario(),
                resultado.errosInesperados().size(), salvos.size());

        assertThat(resultado.errosInesperados()).isEmpty();
        assertThat(resultado.sucessos()).isEqualTo(1);
        assertThat(resultado.conflitosHorario()).isEqualTo(19);
        assertThat(salvos).hasSize(1);
    }

    @Test
    void deve_confirmarTodosOsAgendamentos_quando_dezThreadsReservamHorariosDistintos() throws InterruptedException {
        CenarioBase cenario = criarCenarioBase("distintos");

        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int indice = i;
            OffsetDateTime horario = OffsetDateTime.of(cenario.data(), LocalTime.of(9, 0), ZoneOffset.UTC)
                    .plusMinutes(30L * indice);
            tarefas.add(() -> agendamentoService.criar(
                    new CriarAgendamentoRequest(
                            cenario.profissionalId(),
                            cenario.servico30MinId(),
                            "Cliente " + indice,
                            "distinto" + indice + "@teste.com",
                            horario),
                    cenario.tenantId()));
        }

        ResultadoConcorrencia resultado = executarConcorrente(tarefas);
        List<Agendamento> salvos = agendamentoRepository.findAllByTenantIdOrderByInicioAsc(cenario.tenantId());

        log.info("Cenário C (10 horários distintos) -> threads={}, sucessos={}, conflitos={}, errosInesperados={}, salvosNoBanco={}",
                tarefas.size(), resultado.sucessos(), resultado.conflitosHorario(),
                resultado.errosInesperados().size(), salvos.size());

        assertThat(resultado.errosInesperados()).isEmpty();
        assertThat(resultado.sucessos()).isEqualTo(10);
        assertThat(resultado.conflitosHorario()).isZero();
        assertThat(salvos).hasSize(10);
    }

    @Test
    void deve_confirmarAmbosAgendamentos_quando_doisTenantsReservamMesmoHorarioSimultaneamente()
            throws InterruptedException {
        CenarioBase cenarioA = criarCenarioBase("tenant-a");
        CenarioBase cenarioB = criarCenarioBase("tenant-b");
        OffsetDateTime horario = OffsetDateTime.of(cenarioA.data(), LocalTime.of(9, 0), ZoneOffset.UTC);

        List<Runnable> tarefas = List.of(
                () -> agendamentoService.criar(
                        new CriarAgendamentoRequest(
                                cenarioA.profissionalId(),
                                cenarioA.servico30MinId(),
                                "Cliente A",
                                "clientea@teste.com",
                                horario),
                        cenarioA.tenantId()),
                () -> agendamentoService.criar(
                        new CriarAgendamentoRequest(
                                cenarioB.profissionalId(),
                                cenarioB.servico30MinId(),
                                "Cliente B",
                                "clienteb@teste.com",
                                horario),
                        cenarioB.tenantId()));

        ResultadoConcorrencia resultado = executarConcorrente(tarefas);
        List<Agendamento> salvosA = agendamentoRepository.findAllByTenantIdOrderByInicioAsc(cenarioA.tenantId());
        List<Agendamento> salvosB = agendamentoRepository.findAllByTenantIdOrderByInicioAsc(cenarioB.tenantId());

        log.info("Cenário D (tenants diferentes, mesmo horário) -> threads={}, sucessos={}, conflitos={}, errosInesperados={}, salvosA={}, salvosB={}",
                tarefas.size(), resultado.sucessos(), resultado.conflitosHorario(),
                resultado.errosInesperados().size(), salvosA.size(), salvosB.size());

        assertThat(resultado.errosInesperados()).isEmpty();
        assertThat(resultado.sucessos()).isEqualTo(2);
        assertThat(resultado.conflitosHorario()).isZero();
        assertThat(salvosA).hasSize(1);
        assertThat(salvosB).hasSize(1);
    }

    private ResultadoConcorrencia executarConcorrente(List<Runnable> tarefas) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch prontas = new CountDownLatch(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch concluido = new CountDownLatch(tarefas.size());

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger conflitosHorario = new AtomicInteger();
        List<Throwable> errosInesperados = Collections.synchronizedList(new ArrayList<>());

        try {
            for (Runnable tarefa : tarefas) {
                executor.submit(() -> {
                    prontas.countDown();
                    try {
                        largada.await();
                        tarefa.run();
                        sucessos.incrementAndGet();
                    } catch (HorarioIndisponivelExcecao e) {
                        conflitosHorario.incrementAndGet();
                    } catch (Throwable erro) {
                        errosInesperados.add(erro);
                    } finally {
                        concluido.countDown();
                    }
                });
            }

            if (!prontas.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                fail("Threads não ficaram prontas dentro do timeout de " + TIMEOUT_SEGUNDOS + "s.");
            }
            largada.countDown();
            if (!concluido.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                fail("Execução concorrente excedeu " + TIMEOUT_SEGUNDOS + "s (possível deadlock).");
            }
        } finally {
            executor.shutdownNow();
        }

        return new ResultadoConcorrencia(sucessos.get(), conflitosHorario.get(), errosInesperados);
    }

    private CenarioBase criarCenarioBase(String prefixoSubdominio) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setNome("Clínica " + prefixoSubdominio);
        tenant.setSubdominio(prefixoSubdominio + "-" + sufixo);
        tenant.setAtivo(Boolean.TRUE);
        tenant = tenantRepository.save(tenant);

        Profissional profissional = new Profissional();
        profissional.setTenantId(tenant.getId());
        profissional.setNome("Dr. Concorrência " + sufixo);
        profissional.setEmail("prof-" + sufixo + "@clinica.com");
        profissional.setAtivo(Boolean.TRUE);
        profissional = profissionalRepository.save(profissional);

        ServicoItem servico30 = new ServicoItem();
        servico30.setTenantId(tenant.getId());
        servico30.setProfissional(profissional);
        servico30.setNome("Consulta 30min");
        servico30.setDuracaoMinutos(30);
        servico30.setAtivo(Boolean.TRUE);
        servico30 = servicoItemRepository.save(servico30);

        ServicoItem servico15 = new ServicoItem();
        servico15.setTenantId(tenant.getId());
        servico15.setProfissional(profissional);
        servico15.setNome("Retorno 15min");
        servico15.setDuracaoMinutos(15);
        servico15.setAtivo(Boolean.TRUE);
        servico15 = servicoItemRepository.save(servico15);

        LocalDate dataUtilFutura = LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setTenantId(tenant.getId());
        regra.setProfissional(profissional);
        regra.setDiaSemana(dataUtilFutura.getDayOfWeek());
        regra.setHoraInicio(OffsetTime.of(9, 0, 0, 0, ZoneOffset.UTC));
        regra.setHoraFim(OffsetTime.of(18, 0, 0, 0, ZoneOffset.UTC));
        regra.setAtivo(Boolean.TRUE);
        regraDisponibilidadeRepository.save(regra);

        return new CenarioBase(tenant.getId(), profissional.getId(), servico30.getId(), servico15.getId(),
                dataUtilFutura);
    }

    private record CenarioBase(
            UUID tenantId,
            UUID profissionalId,
            UUID servico30MinId,
            UUID servico15MinId,
            LocalDate data) {
    }

    private record ResultadoConcorrencia(
            int sucessos,
            int conflitosHorario,
            List<Throwable> errosInesperados) {
    }
}
