package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.dto.RegraDisponibilidadeResponse;
import me.lucascustodio.noponto.dto.SalvarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.OffsetTime;
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
import static org.mockito.Mockito.when;

class GestaoRegraDisponibilidadeServiceTest {

    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();

    private RegraDisponibilidadeRepository regraRepository;
    private ProfissionalRepository profissionalRepository;
    private GestaoRegraDisponibilidadeService service;

    @BeforeEach
    void configurar() {
        regraRepository = mock(RegraDisponibilidadeRepository.class);
        profissionalRepository = mock(ProfissionalRepository.class);
        service = new GestaoRegraDisponibilidadeService(regraRepository, profissionalRepository);
        when(regraRepository.save(any(RegraDisponibilidade.class))).thenAnswer(chamada -> {
            RegraDisponibilidade regra = chamada.getArgument(0);
            regra.setId(UUID.randomUUID());
            return regra;
        });
    }

    @Test
    void deve_criarRegra_quando_intervaloValidoSemSobreposicao() {
        profissionalDoTenant();
        when(regraRepository.findAllByTenantIdAndProfissionalIdAndDiaSemana(
                TENANT_ID, PROFISSIONAL_ID, DayOfWeek.MONDAY)).thenReturn(List.of());

        RegraDisponibilidadeResponse resposta = service.criarParaProfissional(
                PROFISSIONAL_ID, requisicao(DayOfWeek.MONDAY, "08:00", "12:00"), TENANT_ID);

        assertThat(resposta.profissionalId()).isEqualTo(PROFISSIONAL_ID);
        assertThat(resposta.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(resposta.horaInicio()).isEqualTo(hora("08:00"));
        assertThat(resposta.horaFim()).isEqualTo(hora("12:00"));
        assertThat(resposta.ativo()).isTrue();
        verify(regraRepository).save(any(RegraDisponibilidade.class));
    }

    @Test
    void deve_lancarExcecao_quando_horaFimNaoEhMaiorQueHoraInicio() {
        assertThatThrownBy(() -> service.criarParaProfissional(
                PROFISSIONAL_ID, requisicao(DayOfWeek.MONDAY, "12:00", "08:00"), TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("A hora de fim deve ser maior que a hora de início.");

        verify(regraRepository, never()).save(any());
        verify(profissionalRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void deve_lancarExcecao_quando_horarioSobrepoeOutraRegraNoMesmoDia() {
        profissionalDoTenant();
        when(regraRepository.findAllByTenantIdAndProfissionalIdAndDiaSemana(
                TENANT_ID, PROFISSIONAL_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(regraExistente(DayOfWeek.MONDAY, "08:00", "12:00")));

        assertThatThrownBy(() -> service.criarParaProfissional(
                PROFISSIONAL_ID, requisicao(DayOfWeek.MONDAY, "10:00", "14:00"), TENANT_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("Não pode haver sobreposição de horários no mesmo dia da semana para o profissional.");

        verify(regraRepository, never()).save(any());
    }

    @Test
    void deve_lancarExcecao_quando_profissionalPertenceAOutroTenant() {
        when(profissionalRepository.findByIdAndTenantId(PROFISSIONAL_ID, OUTRO_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarParaProfissional(
                PROFISSIONAL_ID, requisicao(DayOfWeek.MONDAY, "08:00", "12:00"), OUTRO_TENANT_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class)
                .hasMessage("Profissional não encontrado.");

        verify(regraRepository, never()).save(any());
    }

    private void profissionalDoTenant() {
        Profissional profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setTenantId(TENANT_ID);
        when(profissionalRepository.findByIdAndTenantId(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(profissional));
    }

    private static SalvarRegraDisponibilidadeRequest requisicao(DayOfWeek dia, String inicio, String fim) {
        return new SalvarRegraDisponibilidadeRequest(dia, hora(inicio), hora(fim));
    }

    private static RegraDisponibilidade regraExistente(DayOfWeek dia, String inicio, String fim) {
        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setId(UUID.randomUUID());
        regra.setDiaSemana(dia);
        regra.setHoraInicio(hora(inicio));
        regra.setHoraFim(hora(fim));
        return regra;
    }

    private static OffsetTime hora(String valor) {
        return OffsetTime.of(java.time.LocalTime.parse(valor), BRASILIA);
    }
}
