package me.lucascustodio.noponto.tenant;

import jakarta.servlet.http.HttpServletRequest;
import me.lucascustodio.noponto.exception.TenantNaoIdentificadoExcecao;
import me.lucascustodio.noponto.service.TenantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Resolve o tenant das rotas públicas (sem JWT) pelo subdomínio do host: {@code clinica.noponto.com}.
 * Em ambientes sem subdomínio (ex.: localhost) usa o cabeçalho {@code X-Tenant-Subdominio}.
 */
@Component
public class ResolvedorTenantPublico {

    public static final String CABECALHO_SUBDOMINIO = "X-Tenant-Subdominio";

    private final TenantService tenantService;
    private final String dominioBase;

    public ResolvedorTenantPublico(TenantService tenantService,
                                   @Value("${noponto.tenant.dominio-base}") String dominioBase) {
        this.tenantService = tenantService;
        this.dominioBase = dominioBase.toLowerCase(Locale.ROOT);
    }

    public UUID resolver(HttpServletRequest request) {
        String subdominio = extrairDoHost(request.getServerName());
        if (subdominio == null) {
            subdominio = request.getHeader(CABECALHO_SUBDOMINIO);
        }
        if (subdominio == null || subdominio.isBlank()) {
            throw new TenantNaoIdentificadoExcecao();
        }
        return tenantService.buscarIdPorSubdominio(subdominio.trim());
    }

    private String extrairDoHost(String host) {
        if (host == null) {
            return null;
        }
        String hostNormalizado = host.toLowerCase(Locale.ROOT);
        String sufixo = "." + dominioBase;
        if (!hostNormalizado.endsWith(sufixo) || hostNormalizado.length() == sufixo.length()) {
            return null;
        }
        return hostNormalizado.substring(0, hostNormalizado.length() - sufixo.length());
    }
}
