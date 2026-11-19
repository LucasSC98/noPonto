package me.lucascustodio.noponto.repository;

import jakarta.persistence.LockModeType;
import me.lucascustodio.noponto.domain.Profissional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfissionalRepository extends Repository<Profissional, UUID> {

    Profissional save(Profissional profissional);

    Optional<Profissional> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Profissional> findByIdAndTenantIdAndAtivoTrue(UUID id, UUID tenantId);

    List<Profissional> findAllByTenantIdAndAtivoTrue(UUID tenantId);

    Page<Profissional> findAllByTenantId(UUID tenantId, Pageable pageable);

    /**
     * SELECT ... FOR UPDATE na linha do profissional. Serializa a criação de agendamentos
     * do mesmo profissional, inclusive quando ainda não existe nenhum agendamento para bloquear.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profissional p where p.id = :id and p.tenantId = :tenantId and p.ativo = true")
    Optional<Profissional> buscarAtivoComBloqueio(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
