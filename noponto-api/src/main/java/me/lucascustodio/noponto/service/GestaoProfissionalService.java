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

import java.util.UUID;

@Service
public class GestaoProfissionalService {

    private final ProfissionalRepository profissionalRepository;

    public GestaoProfissionalService(ProfissionalRepository profissionalRepository) {
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalResponse> listar(UUID tenantId, Pageable pageable) {
        return profissionalRepository.findAllByTenantId(tenantId, pageable)
                .map(ProfissionalResponse::de);
    }

    @Transactional
    public ProfissionalResponse criar(CriarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(true);

        return ProfissionalResponse.de(profissionalRepository.save(profissional));
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID id, AtualizarProfissionalRequest requisicao, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        profissional.setNome(requisicao.nome());
        profissional.setEmail(requisicao.email());
        profissional.setAtivo(requisicao.ativo());

        return ProfissionalResponse.de(profissionalRepository.save(profissional));
    }

    @Transactional
    public ProfissionalResponse atualizarAtivo(UUID id, boolean ativo, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
        profissional.setAtivo(ativo);
        return ProfissionalResponse.de(profissionalRepository.save(profissional));
    }
}
