package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import org.springframework.data.repository.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegraDisponibilidadeRepository extends Repository<RegraDisponibilidade, UUID> {

    RegraDisponibilidade save(RegraDisponibilidade regra);

    Optional<RegraDisponibilidade> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RegraDisponibilidade> findAllByTenantIdAndProfissionalId(UUID tenantId, UUID profissionalId);

    List<RegraDisponibilidade> findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(
            UUID tenantId, UUID profissionalId, DayOfWeek diaSemana);
}
