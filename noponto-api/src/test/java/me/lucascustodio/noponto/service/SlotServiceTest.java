package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotServiceTest {

    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();
    /** Sexta-feira. */
    private static final LocalDate DATA = LocalDate.of(2026, 11, 20);

    private ProfissionalService profissionalService;
    private AgendamentoConsultaService agendamentoConsultaService;
    private RegraDisponibilidadeRepository regraRepository;
    private BloqueioRepository bloqueioRepository;
    private ServicoItem servico;

    @BeforeEach
    void configurar() {
        profissionalService = mock(ProfissionalService.class);
        agendamentoConsultaService = mock(AgendamentoConsultaService.class);
        regraRepository = mock(RegraDisponibilidadeRepository.class);
        bloqueioRepository = mock(BloqueioRepository.class);

        servico = new ServicoItem();
        servico.setId(SERVICO_ID);
        servico.setDuracaoMinutos(60);
        when(profissionalService.buscarAtivo(TENANT_ID, PROFISSIONAL_ID)).thenReturn(new Profissional());
        when(profissionalService.buscarServicoAtivo(TENANT_ID, PROFISSIONAL_ID, SERVICO_ID)).thenReturn(servico);
        when(bloqueioRepository.buscarInterceptando(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of());
        when(agendamentoConsultaService.buscarOcupados(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void deve_retornarListaVazia_quando_profissionalSemRegraDeDisponibilidade() {
        when(regraRepository.findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(
                TENANT_ID, PROFISSIONAL_ID, DayOfWeek.FRIDAY)).thenReturn(List.of());

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).isEmpty();
        verifyNoInteractions(bloqueioRepository, agendamentoConsultaService);
    }

    @Test
    void deve_gerarSlotsConsecutivos_quando_janelaSemBloqueiosNemAgendamentos() {
        regraDeSexta("08:00", "12:00");

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"), utc("2026-11-20T12:00:00Z"),
                utc("2026-11-20T13:00:00Z"), utc("2026-11-20T14:00:00Z"));
    }

    @Test
    void deve_descartarSlot_quando_horarioDentroDeUmBloqueio() {
        regraDeSexta("08:00", "12:00");
        when(bloqueioRepository.buscarInterceptando(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(bloqueio("2026-11-20T09:00:00-03:00", "2026-11-20T10:00:00-03:00")));

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"),
                utc("2026-11-20T13:00:00Z"), utc("2026-11-20T14:00:00Z"));
    }

    @Test
    void deve_retornarListaVazia_quando_diaTodoBloqueado() {
        regraDeSexta("08:00", "12:00");
        when(bloqueioRepository.buscarInterceptando(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(bloqueio("2026-11-20T00:00:00-03:00", "2026-11-21T00:00:00-03:00")));

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).isEmpty();
    }

    @Test
    void deve_descartarSlot_quando_agendamentoExistenteOcupaOSlot() {
        regraDeSexta("08:00", "12:00");
        when(agendamentoConsultaService.buscarOcupados(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(agendamento("2026-11-20T10:00:00-03:00", "2026-11-20T11:00:00-03:00")));

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"), utc("2026-11-20T12:00:00Z"),
                utc("2026-11-20T14:00:00Z"));
    }

    @Test
    void deve_descartarSlots_quando_agendamentoSobrepoeParcialmenteOSlot() {
        regraDeSexta("08:00", "12:00");
        // 10:30-11:30 invade metade do slot das 10h e metade do slot das 11h
        when(agendamentoConsultaService.buscarOcupados(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(agendamento("2026-11-20T10:30:00-03:00", "2026-11-20T11:30:00-03:00")));

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"), utc("2026-11-20T12:00:00Z"));
    }

    @Test
    void deve_manterSlotAdjacente_quando_agendamentoTerminaExatamenteNoInicioDoSlot() {
        regraDeSexta("08:00", "10:00");
        when(agendamentoConsultaService.buscarOcupados(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(agendamento("2026-11-20T08:00:00-03:00", "2026-11-20T09:00:00-03:00")));

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T12:00:00Z"));
    }

    @Test
    void deve_naoGerarSlot_quando_duracaoDoServicoMaiorQueAJanela() {
        regraDeSexta("08:00", "12:00");
        servico.setDuracaoMinutos(300);

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).isEmpty();
    }

    @Test
    void deve_ignorarHorarioQueNaoCabeNaJanela_quando_duracaoNaoDivideAJanelaExatamente() {
        regraDeSexta("08:00", "10:30");
        servico.setDuracaoMinutos(60);

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"), utc("2026-11-20T12:00:00Z"));
    }

    @Test
    void deve_descartarSlotsNoPassado_quando_dataEhHoje() {
        regraDeSexta("08:00", "12:00");

        // agora = 09:30 em Brasília (12:30Z): os slots de 08h e 09h já passaram
        List<OffsetDateTime> slots = criarServico("2026-11-20T12:30:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T13:00:00Z"), utc("2026-11-20T14:00:00Z"));
    }

    @Test
    void deve_combinarJanelasSemDuplicar_quando_profissionalTemDuasRegrasNoDia() {
        regraDeSexta("08:00", "10:00");
        regraDeSexta("14:00", "16:00");

        List<OffsetDateTime> slots = criarServico("2026-11-18T12:00:00Z").calcularSlotsDisponiveis(
                PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID);

        assertThat(slots).containsExactly(utc("2026-11-20T11:00:00Z"), utc("2026-11-20T12:00:00Z"),
                utc("2026-11-20T17:00:00Z"), utc("2026-11-20T18:00:00Z"));
    }

    @Test
    void deve_lancarExcecao_quando_profissionalInativoOuInexistente() {
        when(profissionalService.buscarAtivo(TENANT_ID, PROFISSIONAL_ID))
                .thenThrow(new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        assertThatThrownBy(() -> criarServico("2026-11-18T12:00:00Z")
                .calcularSlotsDisponiveis(PROFISSIONAL_ID, DATA, TENANT_ID, SERVICO_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
    }

    @Test
    void deve_confirmarSlotDisponivel_quando_inicioCorrespondeAUmSlotLivre() {
        regraDeSexta("08:00", "12:00");
        SlotService slotService = criarServico("2026-11-18T12:00:00Z");

        assertThat(slotService.slotDisponivel(PROFISSIONAL_ID, utc("2026-11-20T12:00:00Z"), TENANT_ID, SERVICO_ID))
                .isTrue();
        // mesmo instante expresso em outro offset
        assertThat(slotService.slotDisponivel(PROFISSIONAL_ID,
                OffsetDateTime.parse("2026-11-20T09:00:00-03:00"), TENANT_ID, SERVICO_ID)).isTrue();
    }

    @Test
    void deve_recusarSlot_quando_inicioNaoAlinhadoOuOcupado() {
        regraDeSexta("08:00", "12:00");
        when(agendamentoConsultaService.buscarOcupados(eq(TENANT_ID), eq(PROFISSIONAL_ID), any(), any()))
                .thenReturn(List.of(agendamento("2026-11-20T09:00:00-03:00", "2026-11-20T10:00:00-03:00")));
        SlotService slotService = criarServico("2026-11-18T12:00:00Z");

        assertThat(slotService.slotDisponivel(PROFISSIONAL_ID, utc("2026-11-20T12:00:00Z"), TENANT_ID, SERVICO_ID))
                .isFalse();
        assertThat(slotService.slotDisponivel(PROFISSIONAL_ID, utc("2026-11-20T11:30:00Z"), TENANT_ID, SERVICO_ID))
                .isFalse();
    }

    // ---- apoio ----

    private SlotService criarServico(String agoraUtc) {
        Clock relogio = Clock.fixed(Instant.parse(agoraUtc), ZoneOffset.UTC);
        return new SlotService(profissionalService, agendamentoConsultaService, regraRepository,
                bloqueioRepository, relogio);
    }

    private void regraDeSexta(String inicio, String fim) {
        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setDiaSemana(DayOfWeek.FRIDAY);
        regra.setHoraInicio(OffsetTime.of(java.time.LocalTime.parse(inicio), BRASILIA));
        regra.setHoraFim(OffsetTime.of(java.time.LocalTime.parse(fim), BRASILIA));
        List<RegraDisponibilidade> existentes = new java.util.ArrayList<>(
                regraRepository.findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(
                        TENANT_ID, PROFISSIONAL_ID, DayOfWeek.FRIDAY));
        existentes.add(regra);
        when(regraRepository.findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(
                TENANT_ID, PROFISSIONAL_ID, DayOfWeek.FRIDAY)).thenReturn(existentes);
    }

    private static Bloqueio bloqueio(String inicio, String fim) {
        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setInicio(OffsetDateTime.parse(inicio));
        bloqueio.setFim(OffsetDateTime.parse(fim));
        return bloqueio;
    }

    private static Agendamento agendamento(String inicio, String fim) {
        Agendamento agendamento = new Agendamento();
        agendamento.setInicio(OffsetDateTime.parse(inicio));
        agendamento.setFim(OffsetDateTime.parse(fim));
        return agendamento;
    }

    private static OffsetDateTime utc(String instante) {
        return OffsetDateTime.parse(instante).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
