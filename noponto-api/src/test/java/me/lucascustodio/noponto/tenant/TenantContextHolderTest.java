package me.lucascustodio.noponto.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {

    @AfterEach
    void limpar() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_retornarTenant_quando_definidoNaThreadAtual() {
        UUID tenantId = UUID.randomUUID();

        TenantContextHolder.set(tenantId);

        assertThat(TenantContextHolder.get()).isEqualTo(tenantId);
    }

    @Test
    void deve_retornarNulo_quando_contextoLimpo() {
        TenantContextHolder.set(UUID.randomUUID());

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void deve_isolarTenant_quando_threadsDiferentes() throws InterruptedException {
        TenantContextHolder.set(UUID.randomUUID());
        AtomicReference<UUID> tenantEmOutraThread = new AtomicReference<>(UUID.randomUUID());

        Thread outraThread = new Thread(() -> tenantEmOutraThread.set(TenantContextHolder.get()));
        outraThread.start();
        outraThread.join();

        assertThat(tenantEmOutraThread.get()).isNull();
    }
}
