package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RegraDisponibilidadeRepository extends Repository<RegraDisponibilidade, UUID> {

    RegraDisponibilidade save(RegraDisponibilidade regra);

    void delete(RegraDisponibilidade regra);

    Optional<RegraDisponibilidade> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<RegraDisponibilidade> findByIdAndTenantIdAndProfissionalId(UUID id, UUID tenantId, UUID profissionalId);

    List<RegraDisponibilidade> findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(
            UUID tenantId, UUID profissionalId, DayOfWeek diaSemana);

    List<RegraDisponibilidade> findAllByTenantIdAndProfissionalIdAndDiaSemana(
            UUID tenantId, UUID profissionalId, DayOfWeek diaSemana);

    List<RegraDisponibilidade> findAllByTenantIdAndProfissionalId(UUID tenantId, UUID profissionalId);

    Page<RegraDisponibilidade> findAllByTenantIdAndProfissionalId(UUID tenantId, UUID profissionalId, Pageable pageable);

    @Query("""
            select distinct r.profissional.id from RegraDisponibilidade r
            where r.tenantId = :tenantId
              and r.ativo = true
              and r.profissional.id in :profissionalIds
            """)
    Set<UUID> buscarProfissionalIdsComDisponibilidade(@Param("tenantId") UUID tenantId,
                                                      @Param("profissionalIds") Collection<UUID> profissionalIds);
}
