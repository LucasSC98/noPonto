package me.lucascustodio.noponto.tenant;

import jakarta.servlet.http.HttpServletRequest;
import me.lucascustodio.noponto.exception.TenantNaoIdentificadoExcecao;
import me.lucascustodio.noponto.service.TenantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Resolve o tenant das rotas públicas (sem JWT) e popula o {@link TenantContextHolder}
 * antes de qualquer consulta ao banco.
 * Ordem de resolução: subdomínio do host ({@code clinica.noponto.com}) → query param {@code tenant}
 * → cabeçalho {@code X-Tenant-Subdominio} → valor informado no corpo da requisição.
 */
@Component
public class ResolvedorTenantPublico {

    public static final String CABECALHO_SUBDOMINIO = "X-Tenant-Subdominio";
    public static final String PARAMETRO_TENANT = "tenant";

    private final TenantService tenantService;
    private final String dominioBase;

    public ResolvedorTenantPublico(TenantService tenantService,
                                   @Value("${noponto.tenant.dominio-base}") String dominioBase) {
        this.tenantService = tenantService;
        this.dominioBase = dominioBase.toLowerCase(Locale.ROOT);
    }

    public UUID resolver(HttpServletRequest request) {
        return resolver(request, null);
    }

    public UUID resolver(HttpServletRequest request, String tenantNoCorpo) {
        String subdominio = extrairDoHost(request.getServerName());
        if (estaVazio(subdominio)) {
            subdominio = request.getParameter(PARAMETRO_TENANT);
        }
        if (estaVazio(subdominio)) {
            subdominio = request.getHeader(CABECALHO_SUBDOMINIO);
        }
        if (estaVazio(subdominio)) {
            subdominio = tenantNoCorpo;
        }
        if (estaVazio(subdominio)) {
            throw new TenantNaoIdentificadoExcecao();
        }

        UUID tenantId = tenantService.buscarIdPorSubdominio(subdominio.trim());
        TenantContextHolder.set(tenantId);
        return tenantId;
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

    private static boolean estaVazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
