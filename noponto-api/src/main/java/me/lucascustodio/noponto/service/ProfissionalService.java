package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final ServicoItemRepository servicoItemRepository;

    public ProfissionalService(ProfissionalRepository profissionalRepository,
                               ServicoItemRepository servicoItemRepository) {
        this.profissionalRepository = profissionalRepository;
        this.servicoItemRepository = servicoItemRepository;
    }

    /** Profissional inativo (ou de outro tenant) é tratado como inexistente. */
    @Transactional(readOnly = true)
    public Profissional buscarAtivo(UUID tenantId, UUID profissionalId) {
        return profissionalRepository.findByIdAndTenantIdAndAtivoTrue(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
    }

    /**
     * Igual a {@link #buscarAtivo}, mas com SELECT FOR UPDATE na linha do profissional.
     * Deve ser chamado dentro de uma transação de escrita.
     */
    @Transactional
    public Profissional buscarAtivoComBloqueio(UUID tenantId, UUID profissionalId) {
        return profissionalRepository.buscarAtivoComBloqueio(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
    }

    @Transactional(readOnly = true)
    public ServicoItem buscarServicoAtivo(UUID tenantId, UUID profissionalId, UUID servicoItemId) {
        return servicoItemRepository
                .findByIdAndTenantIdAndProfissionalIdAndAtivoTrue(servicoItemId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Serviço não encontrado para o profissional."));
    }
}
