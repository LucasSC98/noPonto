package me.lucascustodio.noponto.tenant;

import jakarta.servlet.FilterChain;
import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private final JwtService jwtService = new JwtService(
            "segredo-de-teste-com-pelo-menos-32-bytes-ok", Duration.ofMinutes(60), Clock.systemUTC());
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtService);

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void deve_popularSecurityContextETenant_quando_tokenValido() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.gerarToken(criarUsuario(tenantId));
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("Authorization", "Bearer " + token);

        AtomicReference<UUID> tenantDuranteRequisicao = new AtomicReference<>();
        AtomicReference<Authentication> autenticacaoDuranteRequisicao = new AtomicReference<>();
        FilterChain cadeia = (req, res) -> {
            tenantDuranteRequisicao.set(TenantContextHolder.get());
            autenticacaoDuranteRequisicao.set(SecurityContextHolder.getContext().getAuthentication());
        };

        filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);

        assertThat(tenantDuranteRequisicao.get()).isEqualTo(tenantId);
        Authentication autenticacao = autenticacaoDuranteRequisicao.get();
        assertThat(autenticacao).isNotNull();
        assertThat(autenticacao.getName()).isEqualTo("ana@clinica.com");
        assertThat(autenticacao.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void deve_limparTenantContext_quando_requisicaoTermina() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("Authorization", "Bearer " + jwtService.gerarToken(criarUsuario(UUID.randomUUID())));

        filtro.doFilter(requisicao, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void deve_naoAutenticar_quando_tokenInvalido() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("Authorization", "Bearer token-invalido");
        AtomicReference<UUID> tenantDuranteRequisicao = new AtomicReference<>();

        filtro.doFilter(requisicao, new MockHttpServletResponse(),
                (req, res) -> tenantDuranteRequisicao.set(TenantContextHolder.get()));

        assertThat(tenantDuranteRequisicao.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deve_naoAutenticar_quando_cabecalhoAusenteOuSemBearer() throws Exception {
        MockHttpServletRequest semCabecalho = new MockHttpServletRequest();
        filtro.doFilter(semCabecalho, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic dXNlcjpzZW5oYQ==");
        filtro.doFilter(basic, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static Usuario criarUsuario(UUID tenantId) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenantId);
        usuario.setEmail("ana@clinica.com");
        usuario.setRole(PerfilUsuario.ADMIN);
        return usuario;
    }
}
