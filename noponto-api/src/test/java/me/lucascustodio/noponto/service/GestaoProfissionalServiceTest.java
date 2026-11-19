package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GestaoProfissionalServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();

    private ProfissionalRepository profissionalRepository;
    private GestaoProfissionalService service;

    @BeforeEach
    void configurar() {
        profissionalRepository = mock(ProfissionalRepository.class);
        service = new GestaoProfissionalService(profissionalRepository);
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> chamada.getArgument(0));
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
        Profissional profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setTenantId(TENANT_ID);
        profissional.setNome("Ana");
        profissional.setEmail("ana@clinica.com");
        profissional.setAtivo(true);
        return profissional;
    }
}
