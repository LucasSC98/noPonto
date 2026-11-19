package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GestaoProfissionalServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();

    private ProfissionalRepository profissionalRepository;
    private GestaoRegraDisponibilidadeService regraDisponibilidadeService;
    private GestaoServicoItemService servicoItemService;
    private AgendamentoConsultaService agendamentoConsultaService;
    private GestaoProfissionalService service;

    @BeforeEach
    void configurar() {
        profissionalRepository = mock(ProfissionalRepository.class);
        regraDisponibilidadeService = mock(GestaoRegraDisponibilidadeService.class);
        servicoItemService = mock(GestaoServicoItemService.class);
        agendamentoConsultaService = mock(AgendamentoConsultaService.class);
        service = new GestaoProfissionalService(
                profissionalRepository,
                regraDisponibilidadeService,
                servicoItemService,
                agendamentoConsultaService);
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(regraDisponibilidadeService.buscarProfissionaisComDisponibilidade(eq(TENANT_ID), anyCollection()))
                .thenReturn(Set.of());
        when(servicoItemService.contarAtivosPorProfissional(eq(TENANT_ID), anyCollection()))
                .thenReturn(Map.of());
        when(agendamentoConsultaService.contarAgendamentosSemanaPorProfissional(eq(TENANT_ID), anyCollection()))
                .thenReturn(Map.of());
    }

    @Test
    void deve_montarResumoEDisponibilidadeNaListagem_quando_profissionaisComESemDados() {
        Profissional comDados = profissional(UUID.randomUUID(), "Dr. João");
        Profissional semDados = profissional(UUID.randomUUID(), "Dra. Maria");
        PageRequest pageable = PageRequest.of(0, 20);
        List<UUID> ids = List.of(comDados.getId(), semDados.getId());
        when(profissionalRepository.findAllByTenantId(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(comDados, semDados)));
        when(regraDisponibilidadeService.buscarProfissionaisComDisponibilidade(TENANT_ID, ids))
                .thenReturn(Set.of(comDados.getId()));
        when(servicoItemService.contarAtivosPorProfissional(TENANT_ID, ids))
                .thenReturn(Map.of(comDados.getId(), 3L));
        when(agendamentoConsultaService.contarAgendamentosSemanaPorProfissional(TENANT_ID, ids))
                .thenReturn(Map.of(comDados.getId(), 5L));

        Page<ProfissionalResponse> resultado = service.listar(TENANT_ID, pageable);

        ProfissionalResponse primeiro = resultado.getContent().get(0);
        ProfissionalResponse segundo = resultado.getContent().get(1);
        assertThat(primeiro.temDisponibilidade()).isTrue();
        assertThat(primeiro.quantidadeServicosAtivos()).isEqualTo(3L);
        assertThat(primeiro.agendamentosSemana()).isEqualTo(5L);
        assertThat(segundo.temDisponibilidade()).isFalse();
        assertThat(segundo.quantidadeServicosAtivos()).isZero();
        assertThat(segundo.agendamentosSemana()).isZero();
    }

    @Test
    void deve_desativarProfissional_quando_solicitadoSemApagarOsDados() {
        Profissional profissional = profissionalAtivo();
        when(profissionalRepository.findByIdAndTenantId(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(profissional));

        ProfissionalResponse resposta = service.atualizarAtivo(PROFISSIONAL_ID, false, TENANT_ID);

        assertThat(resposta.id()).isEqualTo(PROFISSIONAL_ID);
        assertThat(resposta.nome()).isEqualTo("Ana");
        assertThat(resposta.ativo()).isFalse();
        assertThat(profissional.getAtivo()).isFalse();
        verify(profissionalRepository).save(profissional);
    }

    @Test
    void deve_reativarProfissional_quando_estavaInativo() {
        Profissional profissional = profissionalAtivo();
        profissional.setAtivo(false);
        when(profissionalRepository.findByIdAndTenantId(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(profissional));

        ProfissionalResponse resposta = service.atualizarAtivo(PROFISSIONAL_ID, true, TENANT_ID);

        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void deve_lancarExcecao_quando_profissionalDeOutroTenant() {
        when(profissionalRepository.findByIdAndTenantId(PROFISSIONAL_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarAtivo(PROFISSIONAL_ID, false, TENANT_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        verify(profissionalRepository, never()).save(any());
    }

    private static Profissional profissionalAtivo() {
        return profissional(PROFISSIONAL_ID, "Ana");
    }

    private static Profissional profissional(UUID id, String nome) {
        Profissional profissional = new Profissional();
        profissional.setId(id);
        profissional.setTenantId(TENANT_ID);
        profissional.setNome(nome);
        profissional.setEmail(nome.toLowerCase() + "@clinica.com");
        profissional.setAtivo(true);
        return profissional;
    }
}
