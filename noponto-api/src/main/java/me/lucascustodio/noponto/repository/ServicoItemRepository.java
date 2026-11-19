package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.ServicoItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            select new me.lucascustodio.noponto.repository.ContagemPorProfissional(s.profissional.id, count(s))
            from ServicoItem s
            where s.tenantId = :tenantId
              and s.ativo = true
              and s.profissional.id in :profissionalIds
            group by s.profissional.id
            """)
    List<ContagemPorProfissional> contarAtivosPorProfissional(@Param("tenantId") UUID tenantId,
                                                              @Param("profissionalIds") Collection<UUID> profissionalIds);
    @Query("""
            select new me.lucascustodio.noponto.repository.ServicoVinculadoPorProfissional(
                s.profissional.id, s.id, s.nome, s.duracaoMinutos
            )
            from ServicoItem s
            where s.tenantId = :tenantId
              and s.ativo = true
              and s.profissional.id in :profissionalIds
            order by s.nome asc
            """)
    List<ServicoVinculadoPorProfissional> buscarAtivosPorProfissionais(
            @Param("tenantId") UUID tenantId,
            @Param("profissionalIds") Collection<UUID> profissionalIds);
}
