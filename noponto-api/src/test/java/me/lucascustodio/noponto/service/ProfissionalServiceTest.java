package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.dto.ProfissionalPublicoResponse;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfissionalServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICO_ID = UUID.randomUUID();

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

        assertThat(resposta).containsExactly(new ServicoItemResponse(consulta.getId(), "Consulta Geral", 30, true));
    }

    @Test
    void deve_naoListarServicoInativo_quando_consultaPublica() {
        when(profissionalRepository.findByIdAndTenantIdAndAtivoTrue(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(new Profissional()));
        ServicoItem ativo = new ServicoItem();
        ativo.setId(UUID.randomUUID());
        ativo.setNome("Consulta");
        ativo.setDuracaoMinutos(30);
        when(servicoItemRepository.findAllByTenantIdAndProfissionalIdAndAtivoTrue(TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(List.of(ativo));

        List<ServicoItemResponse> resposta = profissionalService.listarServicosAtivos(TENANT_ID, PROFISSIONAL_ID);

        assertThat(resposta).extracting(ServicoItemResponse::id).containsExactly(ativo.getId());
        verify(servicoItemRepository).findAllByTenantIdAndProfissionalIdAndAtivoTrue(TENANT_ID, PROFISSIONAL_ID);
    }

    @Test
    void deve_lancarExcecao_quando_profissionalInativoAoListarServicos() {
        when(profissionalRepository.findByIdAndTenantIdAndAtivoTrue(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> profissionalService.listarServicosAtivos(TENANT_ID, PROFISSIONAL_ID))
                .isInstanceOf(RecursoNaoEncontradoExcecao.class);
        verifyNoInteractions(servicoItemRepository);
    }

    @Test
    void deve_lancarExcecao_quando_profissionalInativoAoAgendar() {
        Profissional inativo = new Profissional();
        inativo.setAtivo(false);
        when(profissionalRepository.buscarComBloqueio(PROFISSIONAL_ID, TENANT_ID)).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> profissionalService.buscarAtivoComBloqueio(TENANT_ID, PROFISSIONAL_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("Profissional inativo não aceita novos agendamentos.");
    }

    @Test
    void deve_retornarNomeEEspecialidadeSemTelefone_quando_buscarProfissionalPublico() {
        Profissional profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setNome("Dr. João Silva");
        profissional.setEspecialidade("Clínico Geral");
        profissional.setTelefone("(11) 99999-9999");
        when(profissionalRepository.findByIdAndTenantIdAndAtivoTrue(PROFISSIONAL_ID, TENANT_ID))
                .thenReturn(Optional.of(profissional));

        ProfissionalPublicoResponse resposta = profissionalService.buscarPublico(TENANT_ID, PROFISSIONAL_ID);

        assertThat(resposta).isEqualTo(new ProfissionalPublicoResponse(PROFISSIONAL_ID, "Dr. João Silva", "Clínico Geral"));
    }

    @Test
    void deve_lancarExcecao_quando_servicoInativoAoAgendar() {
        ServicoItem inativo = new ServicoItem();
        inativo.setAtivo(false);
        when(servicoItemRepository.findByIdAndTenantIdAndProfissionalId(SERVICO_ID, TENANT_ID, PROFISSIONAL_ID))
                .thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> profissionalService.buscarServicoParaAgendamento(
                TENANT_ID, PROFISSIONAL_ID, SERVICO_ID))
                .isInstanceOf(RegraNegocioExcecao.class)
                .hasMessage("Serviço inativo não aceita novos agendamentos.");
    }
}
