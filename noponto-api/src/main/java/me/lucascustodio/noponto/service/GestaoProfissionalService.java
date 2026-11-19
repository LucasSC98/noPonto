package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.dto.AtualizarProfissionalRequest;
import me.lucascustodio.noponto.dto.CriarProfissionalRequest;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GestaoProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final GestaoRegraDisponibilidadeService regraDisponibilidadeService;

    public GestaoProfissionalService(ProfissionalRepository profissionalRepository,
                                     GestaoRegraDisponibilidadeService regraDisponibilidadeService) {
        this.profissionalRepository = profissionalRepository;
        this.regraDisponibilidadeService = regraDisponibilidadeService;
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalResponse> listar(UUID tenantId, Pageable pageable) {
        Page<Profissional> pagina = profissionalRepository.findAllByTenantId(tenantId, pageable);
        List<UUID> ids = pagina.getContent().stream().map(Profissional::getId).toList();
        Set<UUID> comDisponibilidade = regraDisponibilidadeService.buscarProfissionaisComDisponibilidade(tenantId, ids);

        return pagina.map(profissional ->
                ProfissionalResponse.de(profissional, comDisponibilidade.contains(profissional.getId())));
    }

    @Transactional
    public ProfissionalResponse criar(CriarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(true);

        return ProfissionalResponse.de(profissionalRepository.save(profissional), false);
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID id, AtualizarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(requisicao.ativo());

        Profissional salvo = profissionalRepository.save(profissional);
        boolean temDisponibilidade = regraDisponibilidadeService
                .buscarProfissionaisComDisponibilidade(tenantId, List.of(salvo.getId()))
                .contains(salvo.getId());
        return ProfissionalResponse.de(salvo, temDisponibilidade);
    }

    @Transactional
    public ProfissionalResponse atualizarAtivo(UUID id, boolean ativo, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
        profissional.setAtivo(ativo);

        Profissional salvo = profissionalRepository.save(profissional);
        boolean temDisponibilidade = regraDisponibilidadeService
                .buscarProfissionaisComDisponibilidade(tenantId, List.of(salvo.getId()))
                .contains(salvo.getId());
        return ProfissionalResponse.de(salvo, temDisponibilidade);
    }
}
