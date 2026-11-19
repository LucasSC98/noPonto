package me.lucascustodio.noponto.tenant;

import me.lucascustodio.noponto.exception.TenantNaoIdentificadoExcecao;
import me.lucascustodio.noponto.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolvedorTenantPublicoTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private TenantService tenantService;
    private ResolvedorTenantPublico resolvedor;

    @BeforeEach
    void configurar() {
        tenantService = mock(TenantService.class);
        resolvedor = new ResolvedorTenantPublico(tenantService, "noponto.com");
        when(tenantService.buscarIdPorSubdominio("clinica-teste")).thenReturn(TENANT_ID);
    }

    @AfterEach
    void limpar() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_resolverTenantEPopularContexto_quando_queryParamTenantInformadoEmLocalhost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/profissionais/1/servicos");
        request.setServerName("localhost");
        request.setParameter("tenant", "clinica-teste");

        UUID resolvido = resolvedor.resolver(request);

        assertThat(resolvido).isEqualTo(TENANT_ID);
        assertThat(TenantContextHolder.get()).isEqualTo(TENANT_ID);
    }

    @Test
    void deve_resolverTenantEPopularContexto_quando_tenantInformadoNoCorpoEmLocalhost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/agendamentos");
        request.setServerName("localhost");

        UUID resolvido = resolvedor.resolver(request, "clinica-teste");

        assertThat(resolvido).isEqualTo(TENANT_ID);
        assertThat(TenantContextHolder.get()).isEqualTo(TENANT_ID);
    }

    @Test
    void deve_resolverTenantEPopularContexto_quando_subdominioNoHost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/profissionais/1/slots");
        request.setServerName("clinica-teste.noponto.com");

        UUID resolvido = resolvedor.resolver(request);

        assertThat(resolvido).isEqualTo(TENANT_ID);
        assertThat(TenantContextHolder.get()).isEqualTo(TENANT_ID);
    }

    @Test
    void deve_resolverTenantEPopularContexto_quando_cabecalhoSubdominioInformado() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/profissionais/1/slots");
        request.setServerName("localhost");
        request.addHeader(ResolvedorTenantPublico.CABECALHO_SUBDOMINIO, "clinica-teste");

        UUID resolvido = resolvedor.resolver(request);

        assertThat(resolvido).isEqualTo(TENANT_ID);
        assertThat(TenantContextHolder.get()).isEqualTo(TENANT_ID);
    }

    @Test
    void deve_lancarExcecao_quando_nenhumTenantInformadoEmLocalhost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/profissionais/1/servicos");
        request.setServerName("localhost");

        assertThatThrownBy(() -> resolvedor.resolver(request))
                .isInstanceOf(TenantNaoIdentificadoExcecao.class);
        assertThat(TenantContextHolder.get()).isNull();
    }
}
