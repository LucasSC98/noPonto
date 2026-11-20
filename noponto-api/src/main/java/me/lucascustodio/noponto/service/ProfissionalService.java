package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.dto.ProfissionalPublicoResponse;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    /** Profissional do tenant (independente de estar ativo ou inativo). */
    @Transactional(readOnly = true)
    public Profissional buscarDoTenant(UUID tenantId, UUID profissionalId) {
        return profissionalRepository.findByIdAndTenantId(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
    }

    /** Profissional inativo (ou de outro tenant) é tratado como inexistente. */
    @Transactional(readOnly = true)
    public Profissional buscarAtivo(UUID tenantId, UUID profissionalId) {
        return profissionalRepository.findByIdAndTenantIdAndAtivoTrue(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
    }

    /**
     * SELECT FOR UPDATE na linha do profissional, dentro de uma transação de escrita.
     * Recusa novo agendamento quando o profissional está inativo, sem alterar os já existentes.
     */
    @Transactional
    public Profissional buscarAtivoComBloqueio(UUID tenantId, UUID profissionalId) {
        Profissional profissional = profissionalRepository.buscarComBloqueio(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
        if (!Boolean.TRUE.equals(profissional.getAtivo())) {
            throw new RegraNegocioExcecao("Profissional inativo não aceita novos agendamentos.");
        }
        return profissional;
    }

    @Transactional(readOnly = true)
    public ServicoItem buscarServicoParaAgendamento(UUID tenantId, UUID profissionalId, UUID servicoItemId) {
        ServicoItem servico = servicoItemRepository
                .findByIdAndTenantIdAndProfissionalId(servicoItemId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Serviço não encontrado para o profissional."));
        if (!Boolean.TRUE.equals(servico.getAtivo())) {
            throw new RegraNegocioExcecao("Serviço inativo não aceita novos agendamentos.");
        }
        return servico;
    }

    @Transactional(readOnly = true)
    public ServicoItem buscarServicoAtivo(UUID tenantId, UUID profissionalId, UUID servicoItemId) {
        return servicoItemRepository
                .findByIdAndTenantIdAndProfissionalIdAndAtivoTrue(servicoItemId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Serviço não encontrado para o profissional."));
    }

    @Transactional(readOnly = true)
    public ProfissionalPublicoResponse buscarPublico(UUID tenantId, UUID profissionalId) {
        return ProfissionalPublicoResponse.de(buscarAtivo(tenantId, profissionalId));
    }

    @Transactional(readOnly = true)
    public List<ServicoItemResponse> listarServicosAtivos(UUID tenantId, UUID profissionalId) {
        buscarAtivo(tenantId, profissionalId);
        return servicoItemRepository
                .findAllByTenantIdAndProfissionalIdAndAtivoTrue(tenantId, profissionalId)
                .stream()
                .map(ServicoItemResponse::de)
                .toList();
    }
}
