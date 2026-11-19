package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.Bloqueio;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BloqueioRepository extends Repository<Bloqueio, UUID> {

    Bloqueio save(Bloqueio bloqueio);

    /** Bloqueios que interceptam o intervalo {@code [inicio, fim)}. */
    @Query("""
            select b from Bloqueio b
            where b.tenantId = :tenantId
              and b.profissional.id = :profissionalId
              and b.inicio < :fim
              and b.fim > :inicio
            order by b.inicio
            """)
    List<Bloqueio> buscarInterceptando(@Param("tenantId") UUID tenantId,
                                       @Param("profissionalId") UUID profissionalId,
                                       @Param("inicio") OffsetDateTime inicio,
                                       @Param("fim") OffsetDateTime fim);
}
