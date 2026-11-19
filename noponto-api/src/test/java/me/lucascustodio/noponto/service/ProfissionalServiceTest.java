package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfissionalServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();

    private ProfissionalRepository profissionalRepository;
    private ServicoItemRepository servicoItemRepository;
    private ProfissionalService profissionalService;

    @BeforeEach
    void configurar() {
        profissionalRepository = mock(ProfissionalRepository.class);
        servicoItemRepository = mock(ServicoItemRepository.class);
        profissionalService = new ProfissionalService(profissionalRepository, servicoItemRepository);
    }

    @Test
    void deve_retornarServicosAtivos_quando_profissionalAtivo() {
        when(profissionalRepository.findByIdAndTenantIdAndAtivoTrue(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(new Profissional()));
        ServicoItem consulta = new ServicoItem();
        consulta.setId(UUID.randomUUID());
        consulta.setNome("Consulta Geral");
        consulta.setDuracaoMinutos(30);
        when(servicoItemRepository.findAllByTenantIdAndProfissionalIdAndAtivoTrue(TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(List.of(consulta));

        List<ServicoItemResponse> resposta = profissionalService.listarServicosAtivos(TENANT_ID, PROFISSIONAL_ID);

        assertThat(resposta).containsExactly(new ServicoItemResponse(consulta.getId(), "Consulta Geral", 30));
    }

    @Test
    void deve_lancarExcecao_quando_profissionalInativoAoListarServicos() {
        when(profissionalRepository.findByIdAndTenantIdAndAtivoTrue(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> profissionalService.listarServicosAtivos(TENANT_ID, PROFISSIONAL_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        verifyNoInteractions(servicoItemRepository);
    }
}
