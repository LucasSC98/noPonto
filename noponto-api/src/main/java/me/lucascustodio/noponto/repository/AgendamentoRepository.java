package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends Repository<Agendamento, UUID> {

    Agendamento save(Agendamento agendamento);

    Optional<Agendamento> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Agendamento> findAllByTenantIdOrderByInicioAsc(UUID tenantId);

    /** Agendamentos com os status informados que interceptam o intervalo {@code [inicio, fim)}. */
    @Query("""
            select a from Agendamento a
            where a.tenantId = :tenantId
              and a.profissional.id = :profissionalId
              and a.status in :status
              and a.inicio < :fim
              and a.fim > :inicio
            order by a.inicio
            """)
    List<Agendamento> buscarInterceptando(@Param("tenantId") UUID tenantId,
                                          @Param("profissionalId") UUID profissionalId,
                                          @Param("status") List<StatusAgendamento> status,
                                          @Param("inicio") OffsetDateTime inicio,
                                          @Param("fim") OffsetDateTime fim);
}
