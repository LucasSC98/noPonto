package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.Tenant;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends Repository<Tenant, UUID> {

    Tenant save(Tenant tenant);

    Optional<Tenant> findByIdAndAtivoTrue(UUID id);

    Optional<Tenant> findBySubdominioIgnoreCaseAndAtivoTrue(String subdominio);
}
