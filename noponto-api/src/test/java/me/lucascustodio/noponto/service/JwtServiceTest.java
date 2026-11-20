package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-bytes-ok";
    private static final Instant AGORA = Instant.parse("2026-11-18T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(60);

    private final JwtService jwtService = criarServico(AGORA);

    @Test
    void deve_gerarTokenComClaims_quando_usuarioValido() {
        Usuario usuario = criarUsuario(PerfilUsuario.PROFISSIONAL);

        String token = jwtService.gerarToken(usuario);

        assertThat(jwtService.tokenValido(token)).isTrue();
        assertThat(jwtService.extrairEmail(token)).isEqualTo("ana@clinica.com");
        assertThat(jwtService.extrairTenantId(token)).isEqualTo(usuario.getTenantId());
        assertThat(jwtService.extrairRole(token)).isEqualTo(PerfilUsuario.PROFISSIONAL);
    }

    @Test
    void deve_retornarFalso_quando_tokenExpirado() {
        String token = jwtService.gerarToken(criarUsuario(PerfilUsuario.ADMIN));
        JwtService servicoNoFuturo = criarServico(AGORA.plus(VALIDADE).plusSeconds(1));

        assertThat(servicoNoFuturo.tokenValido(token)).isFalse();
    }

    @Test
    void deve_retornarVerdadeiro_quando_tokenAindaDentroDaValidade() {
        String token = jwtService.gerarToken(criarUsuario(PerfilUsuario.ADMIN));
        JwtService servicoQuaseExpirando = criarServico(AGORA.plus(VALIDADE).minusSeconds(1));

        assertThat(servicoQuaseExpirando.tokenValido(token)).isTrue();
    }

    @Test
    void deve_retornarFalso_quando_tokenAssinadoComOutroSegredo() {
        JwtService outroServico = new JwtService("outro-segredo-com-pelo-menos-32-bytes-tambem",
                VALIDADE, Clock.fixed(AGORA, ZoneOffset.UTC));
        String tokenDeOutraChave = outroServico.gerarToken(criarUsuario(PerfilUsuario.ADMIN));

        assertThat(jwtService.tokenValido(tokenDeOutraChave)).isFalse();
    }

    @Test
    void deve_retornarFalso_quando_tokenAdulterado() {
        String token = jwtService.gerarToken(criarUsuario(PerfilUsuario.ADMIN));
        String adulterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtService.tokenValido(adulterado)).isFalse();
    }

    @Test
    void deve_retornarFalso_quando_tokenVazioOuMalformado() {
        assertThat(jwtService.tokenValido(null)).isFalse();
        assertThat(jwtService.tokenValido("")).isFalse();
        assertThat(jwtService.tokenValido("nao-e-um-jwt")).isFalse();
    }

    @Test
    void deve_falharAoIniciar_quando_segredoAusente() {
        assertThatThrownBy(() -> new JwtService(null, VALIDADE, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes");
        assertThatThrownBy(() -> new JwtService("   ", VALIDADE, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes");
    }

    @Test
    void deve_falharAoIniciar_quando_segredoMenorQue32Bytes() {
        assertThatThrownBy(() -> new JwtService("curto", VALIDADE, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes");
    }

    @Test
    void deve_iniciar_quando_segredoTem32Bytes() {
        JwtService servico = new JwtService("12345678901234567890123456789012", VALIDADE, Clock.systemUTC());

        assertThat(servico.tokenValido(servico.gerarToken(criarUsuario(PerfilUsuario.ADMIN)))).isTrue();
    }

    private static JwtService criarServico(Instant instante) {
        return new JwtService(SEGREDO, VALIDADE, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private static Usuario criarUsuario(PerfilUsuario role) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(UUID.randomUUID());
        usuario.setEmail("ana@clinica.com");
        usuario.setRole(role);
        return usuario;
    }
}
