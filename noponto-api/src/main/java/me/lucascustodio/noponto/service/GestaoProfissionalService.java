package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.dto.AtualizarProfissionalRequest;
import me.lucascustodio.noponto.dto.CriarProfissionalRequest;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.dto.ServicoVinculadoResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GestaoProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final GestaoRegraDisponibilidadeService regraDisponibilidadeService;
    private final GestaoServicoItemService servicoItemService;
    private final AgendamentoConsultaService agendamentoConsultaService;

    public GestaoProfissionalService(ProfissionalRepository profissionalRepository,
                                     GestaoRegraDisponibilidadeService regraDisponibilidadeService,
                                     GestaoServicoItemService servicoItemService,
                                     AgendamentoConsultaService agendamentoConsultaService) {
        this.profissionalRepository = profissionalRepository;
        this.regraDisponibilidadeService = regraDisponibilidadeService;
        this.servicoItemService = servicoItemService;
        this.agendamentoConsultaService = agendamentoConsultaService;
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalResponse> listar(UUID tenantId, Pageable pageable) {
        Page<Profissional> pagina = profissionalRepository.findAllByTenantId(tenantId, pageable);
        List<UUID> ids = pagina.getContent().stream().map(Profissional::getId).toList();
        if (ids.isEmpty()) {
            return pagina.map(ProfissionalResponse::de);
        }

        Set<UUID> comDisponibilidade = regraDisponibilidadeService
                .buscarProfissionaisComDisponibilidade(tenantId, ids);
        Map<UUID, List<ServicoVinculadoResponse>> servicosPorProfissional = servicoItemService
                .listarAtivosPorProfissionais(tenantId, ids);
        Map<UUID, Long> agendamentosSemana = agendamentoConsultaService
                .contarAgendamentosSemanaPorProfissional(tenantId, ids);

        return pagina.map(profissional -> ProfissionalResponse.de(
                profissional,
                comDisponibilidade.contains(profissional.getId()),
                agendamentosSemana.getOrDefault(profissional.getId(), 0L),
                servicosPorProfissional.getOrDefault(profissional.getId(), List.of())
        ));
    }

    @Transactional
    public ProfissionalResponse criar(CriarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(true);

        return ProfissionalResponse.de(profissionalRepository.save(profissional), false, 0L, List.of());
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID id, AtualizarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(requisicao.ativo());

        return montarResposta(profissionalRepository.save(profissional), tenantId);
    }

    @Transactional
    public ProfissionalResponse atualizarAtivo(UUID id, boolean ativo, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
        profissional.setAtivo(ativo);

        return montarResposta(profissionalRepository.save(profissional), tenantId);
    }

    private ProfissionalResponse montarResposta(Profissional profissional, UUID tenantId) {
        List<UUID> ids = List.of(profissional.getId());
        boolean temDisponibilidade = regraDisponibilidadeService
                .buscarProfissionaisComDisponibilidade(tenantId, ids)
                .contains(profissional.getId());
        List<ServicoVinculadoResponse> servicos = servicoItemService
                .listarAtivosPorProfissionais(tenantId, ids)
                .getOrDefault(profissional.getId(), List.of());
        long agendamentosSemana = agendamentoConsultaService
                .contarAgendamentosSemanaPorProfissional(tenantId, ids)
                .getOrDefault(profissional.getId(), 0L);

        return ProfissionalResponse.de(profissional, temDisponibilidade, agendamentosSemana, servicos);
    }
}
