package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    /**
     * Única consulta sem filtro de tenant: usada exclusivamente no login,
     * momento em que o tenant ainda não é conhecido. O e-mail é único por tenant,
     * então pode retornar mais de um usuário.
     */
    List<Usuario> findAllByEmailIgnoreCaseAndAtivoTrue(String email);
}
