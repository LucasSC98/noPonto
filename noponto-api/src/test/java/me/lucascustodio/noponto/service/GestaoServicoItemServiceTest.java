package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GestaoServicoItemServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();

    private ServicoItemRepository servicoItemRepository;
    private GestaoServicoItemService service;

    @BeforeEach
    void configurar() {
        servicoItemRepository = mock(ServicoItemRepository.class);
        service = new GestaoServicoItemService(servicoItemRepository, mock(ProfissionalRepository.class));
        when(servicoItemRepository.save(any(ServicoItem.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void deve_desativarServico_quando_solicitadoSemApagarOsDados() {
        ServicoItem servico = new ServicoItem();
        servico.setId(SERVICO_ID);
        servico.setNome("Consulta");
        servico.setDuracaoMinutos(30);
        servico.setAtivo(true);
        when(servicoItemRepository.findByIdAndTenantId(SERVICO_ID, TENANT_ID)).thenReturn(Optional.of(servico));

        ServicoItemResponse resposta = service.atualizarAtivo(SERVICO_ID, false, TENANT_ID);

        assertThat(resposta.id()).isEqualTo(SERVICO_ID);
        assertThat(resposta.nome()).isEqualTo("Consulta");
        assertThat(resposta.duracaoMinutos()).isEqualTo(30);
        assertThat(resposta.ativo()).isFalse();
    }

    @Test
    void deve_reativarServico_quando_estavaInativo() {
        ServicoItem servico = new ServicoItem();
        servico.setId(SERVICO_ID);
        servico.setNome("Consulta");
        servico.setDuracaoMinutos(30);
        servico.setAtivo(false);
        when(servicoItemRepository.findByIdAndTenantId(SERVICO_ID, TENANT_ID)).thenReturn(Optional.of(servico));

        ServicoItemResponse resposta = service.atualizarAtivo(SERVICO_ID, true, TENANT_ID);

        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.nome()).isEqualTo("Consulta");
    }
}
