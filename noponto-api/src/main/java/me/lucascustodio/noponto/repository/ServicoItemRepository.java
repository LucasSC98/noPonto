package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.ServicoItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicoItemRepository extends Repository<ServicoItem, UUID> {

    ServicoItem save(ServicoItem servicoItem);

    Optional<ServicoItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ServicoItem> findByIdAndTenantIdAndProfissionalId(UUID id, UUID tenantId, UUID profissionalId);

    Optional<ServicoItem> findByIdAndTenantIdAndProfissionalIdAndAtivoTrue(UUID id, UUID tenantId, UUID profissionalId);

    List<ServicoItem> findAllByTenantIdAndProfissionalIdAndAtivoTrue(UUID tenantId, UUID profissionalId);

    Page<ServicoItem> findAllByTenantIdAndProfissionalId(UUID tenantId, UUID profissionalId, Pageable pageable);
}
