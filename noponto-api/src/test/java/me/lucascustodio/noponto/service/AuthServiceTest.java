package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.dto.LoginRequest;
import me.lucascustodio.noponto.dto.LoginResponse;
import me.lucascustodio.noponto.exception.CredenciaisInvalidasExcecao;
import me.lucascustodio.noponto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String EMAIL = "ana@clinica.com";
    private static final String SENHA = "senha-correta";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(
            "segredo-de-teste-com-pelo-menos-32-bytes-ok", Duration.ofMinutes(60), Clock.systemUTC());
    private UsuarioRepository usuarioRepository;
    private AuthService authService;

    @BeforeEach
    void configurar() {
        usuarioRepository = mock(UsuarioRepository.class);
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService, 60);
    }

    @Test
    void deve_retornarToken_quando_credenciaisCorretas() {
        Usuario usuario = criarUsuario(SENHA);
        when(usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(EMAIL)).thenReturn(List.of(usuario));

        LoginResponse resposta = authService.autenticar(new LoginRequest(EMAIL, SENHA));

        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.expiraEmSegundos()).isEqualTo(3600);
        assertThat(jwtService.extrairTenantId(resposta.token())).isEqualTo(usuario.getTenantId());
    }

    @Test
    void deve_lancarExcecao_quando_senhaIncorreta() {
        when(usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(EMAIL)).thenReturn(List.of(criarUsuario(SENHA)));

        assertThatThrownBy(() -> authService.autenticar(new LoginRequest(EMAIL, "senha-errada")))
                .isInstanceOf(CredenciaisInvalidasExcecao.class);
    }

    @Test
    void deve_lancarExcecao_quando_emailInexistente() {
        when(usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.autenticar(new LoginRequest(EMAIL, SENHA)))
                .isInstanceOf(CredenciaisInvalidasExcecao.class);
    }

    @Test
    void deve_autenticarNoTenantCorreto_quando_mesmoEmailEmTenantsDiferentesComSenhasDiferentes() {
        Usuario usuarioTenantA = criarUsuario(SENHA);
        Usuario usuarioTenantB = criarUsuario("outra-senha");
        when(usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(EMAIL))
                .thenReturn(List.of(usuarioTenantA, usuarioTenantB));

        LoginResponse resposta = authService.autenticar(new LoginRequest(EMAIL, SENHA));

        assertThat(jwtService.extrairTenantId(resposta.token())).isEqualTo(usuarioTenantA.getTenantId());
    }

    @Test
    void deve_lancarExcecao_quando_mesmasCredenciaisValidasEmMaisDeUmTenant() {
        when(usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(EMAIL))
                .thenReturn(List.of(criarUsuario(SENHA), criarUsuario(SENHA)));

        assertThatThrownBy(() -> authService.autenticar(new LoginRequest(EMAIL, SENHA)))
                .isInstanceOf(CredenciaisInvalidasExcecao.class);
    }

    private Usuario criarUsuario(String senha) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(UUID.randomUUID());
        usuario.setEmail(EMAIL);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setRole(PerfilUsuario.ADMIN);
        return usuario;
    }
}
