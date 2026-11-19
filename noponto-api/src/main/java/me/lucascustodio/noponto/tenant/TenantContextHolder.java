package me.lucascustodio.noponto.tenant;

import java.util.UUID;

/**
 * Mantém o tenant da requisição corrente em um ThreadLocal.
 * Populado pelo {@link JwtAuthFilter} e sempre limpo ao final da requisição.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT_ATUAL = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(UUID tenantId) {
        TENANT_ATUAL.set(tenantId);
    }

    public static UUID get() {
        return TENANT_ATUAL.get();
    }

    public static void clear() {
        TENANT_ATUAL.remove();
    }
}
