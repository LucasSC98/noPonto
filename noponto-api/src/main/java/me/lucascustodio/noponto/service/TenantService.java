package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Tenant buscarPorId(UUID tenantId) {
        return tenantRepository.findByIdAndAtivoTrue(tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Estabelecimento não encontrado."));
    }

    @Transactional(readOnly = true)
    public UUID buscarIdPorSubdominio(String subdominio) {
        return tenantRepository.findBySubdominioIgnoreCaseAndAtivoTrue(subdominio)
                .map(Tenant::getId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Estabelecimento não encontrado."));
    }
}
