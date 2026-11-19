package me.lucascustodio.noponto.service;

import jakarta.persistence.EntityManager;
import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.dto.ServicoVinculadoResponse;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ResumoProfissionalIntegracaoTest {

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
    private GestaoProfissionalService gestaoProfissionalService;

    @Autowired
    private Clock relogio;

    @Test
    void deve_contarServicosAtivosEAgendamentosDaSemana_quando_ignoraInativoCanceladoConcluidoEOutroTenant() {
        Tenant tenantA = criarTenant("resumo-a-" + UUID.randomUUID().toString().substring(0, 8));
        Tenant tenantB = criarTenant("resumo-b-" + UUID.randomUUID().toString().substring(0, 8));

        Profissional profA = criarProfissional(tenantA.getId(), "Dr. João Silva");
        Profissional profB = criarProfissional(tenantB.getId(), "Dr. Outro Tenant");

        ServicoItem consultaGeral = criarServico(tenantA.getId(), profA, "Consulta Geral", 30, true);
        criarServico(tenantA.getId(), profA, "Retorno", 15, true);
        criarServico(tenantA.getId(), profA, "Avaliação", 15, true);
        criarServico(tenantA.getId(), profA, "Inativo", 20, false);
        ServicoItem servicoB = criarServico(tenantB.getId(), profB, "Serviço B", 30, true);

        LocalDate segundaAtual = LocalDate.now(relogio.withZone(ZoneOffset.UTC))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime quartaUtc = segundaAtual.plusDays(2).atTime(10, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime semanaPassadaUtc = segundaAtual.minusDays(2).atTime(10, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime proximaSemanaUtc = segundaAtual.plusDays(7).atTime(10, 0).atOffset(ZoneOffset.UTC);

        // Contam: PENDENTE e CONFIRMADO na semana atual do tenant A
        criarAgendamento(tenantA.getId(), profA, consultaGeral, quartaUtc, StatusAgendamento.PENDENTE);
        criarAgendamento(tenantA.getId(), profA, consultaGeral, quartaUtc.plusHours(1), StatusAgendamento.CONFIRMADO);

        // Não contam: CANCELADO, CONCLUIDO, fora da semana e outro tenant
        criarAgendamento(tenantA.getId(), profA, consultaGeral, quartaUtc.plusHours(2), StatusAgendamento.CANCELADO);
        criarAgendamento(tenantA.getId(), profA, consultaGeral, quartaUtc.plusHours(3), StatusAgendamento.CONCLUIDO);
        criarAgendamento(tenantA.getId(), profA, consultaGeral, semanaPassadaUtc, StatusAgendamento.CONFIRMADO);
        criarAgendamento(tenantA.getId(), profA, consultaGeral, proximaSemanaUtc, StatusAgendamento.CONFIRMADO);
        criarAgendamento(tenantB.getId(), profB, servicoB, quartaUtc, StatusAgendamento.CONFIRMADO);

        // Limpa cache L1 do Hibernate para garantir leitura real do PostgreSQL sem proxies em memória
        entityManager.flush();
        entityManager.clear();

        Page<ProfissionalResponse> pagina = gestaoProfissionalService.listar(tenantA.getId(), PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        ProfissionalResponse resposta = pagina.getContent().getFirst();
        assertThat(resposta.id()).isEqualTo(profA.getId());
        assertThat(resposta.quantidadeServicosAtivos()).isEqualTo(3L);
        assertThat(resposta.agendamentosSemana()).isEqualTo(2L);
        assertThat(resposta.servicosAtivos())
                .extracting(ServicoVinculadoResponse::nome)
                .containsExactly("Avaliação", "Consulta Geral", "Retorno");
    }

    @Test
    void deve_indicarSemDisponibilidade_quando_profissionalAtivoNaoPossuiRegraDeDisponibilidade() {
        Tenant tenant = criarTenant("disp-" + UUID.randomUUID().toString().substring(0, 8));
        Profissional semHorarios = criarProfissional(tenant.getId(), "Ana Sem Horários");
        Profissional comHorarios = criarProfissional(tenant.getId(), "Bruno Com Horários");

        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setTenantId(tenant.getId());
        regra.setProfissional(comHorarios);
        regra.setDiaSemana(DayOfWeek.MONDAY);
        regra.setHoraInicio(OffsetTime.of(9, 0, 0, 0, ZoneOffset.ofHours(-3)));
        regra.setHoraFim(OffsetTime.of(18, 0, 0, 0, ZoneOffset.ofHours(-3)));
        regra.setAtivo(true);
        regraDisponibilidadeRepository.save(regra);

        entityManager.flush();
        entityManager.clear();

        Page<ProfissionalResponse> pagina = gestaoProfissionalService.listar(
                tenant.getId(), PageRequest.of(0, 10, Sort.by("nome").ascending()));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).id()).isEqualTo(semHorarios.getId());
        assertThat(pagina.getContent().get(0).temDisponibilidade()).isFalse();
        assertThat(pagina.getContent().get(1).id()).isEqualTo(comHorarios.getId());
        assertThat(pagina.getContent().get(1).temDisponibilidade()).isTrue();
    }

    private Tenant criarTenant(String subdominio) {
        Tenant tenant = new Tenant();
        tenant.setNome("Clínica " + subdominio);
        tenant.setSubdominio(subdominio);
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

    private ServicoItem criarServico(UUID tenantId,
                                     Profissional profissional,
                                     String nome,
                                     int duracaoMinutos,
                                     boolean ativo) {
        ServicoItem servico = new ServicoItem();
        servico.setTenantId(tenantId);
        servico.setProfissional(profissional);
        servico.setNome(nome);
        servico.setDuracaoMinutos(duracaoMinutos);
        servico.setAtivo(ativo);
        return servicoItemRepository.save(servico);
    }

    private void criarAgendamento(UUID tenantId,
                                  Profissional profissional,
                                  ServicoItem servico,
                                  OffsetDateTime inicio,
                                  StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente("Cliente Teste");
        agendamento.setEmailCliente("cliente@teste.com");
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plusMinutes(30));
        agendamento.setStatus(status);
        agendamentoRepository.save(agendamento);
    }
}
