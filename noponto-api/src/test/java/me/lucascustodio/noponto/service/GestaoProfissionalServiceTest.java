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
    private GestaoProfissionalService service;

    @BeforeEach
    void configurar() {
        profissionalRepository = mock(ProfissionalRepository.class);
        regraDisponibilidadeService = mock(GestaoRegraDisponibilidadeService.class);
        service = new GestaoProfissionalService(profissionalRepository, regraDisponibilidadeService);
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(regraDisponibilidadeService.buscarProfissionaisComDisponibilidade(eq(TENANT_ID), anyCollection()))
                .thenReturn(Set.of());
    }

    @Test
    void deve_indicarDisponibilidadeNaListagem_quando_profissionaisComESemRegras() {
        Profissional comRegra = profissional(UUID.randomUUID(), "Dr. João");
        Profissional semRegra = profissional(UUID.randomUUID(), "Dra. Maria");
        PageRequest pageable = PageRequest.of(0, 20);
        when(profissionalRepository.findAllByTenantId(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(comRegra, semRegra)));
        when(regraDisponibilidadeService.buscarProfissionaisComDisponibilidade(
                TENANT_ID, List.of(comRegra.getId(), semRegra.getId())))
                .thenReturn(Set.of(comRegra.getId()));

        Page<ProfissionalResponse> resultado = service.listar(TENANT_ID, pageable);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.getContent().get(0).temDisponibilidade()).isTrue();
        assertThat(resultado.getContent().get(1).temDisponibilidade()).isFalse();
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
