package me.lucascustodio.noponto.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLE = "role";

    private static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;

    private final SecretKey chaveAssinatura;
    private final Duration validade;
    private final Clock relogio;

    @Autowired
    public JwtService(@Value("${noponto.jwt.segredo}") String segredo,
                      @Value("${noponto.jwt.expiracao-minutos}") long expiracaoMinutos) {
        this(segredo, Duration.ofMinutes(expiracaoMinutos), Clock.systemUTC());
    }

    public JwtService(String segredo, Duration validade, Clock relogio) {
        byte[] bytesSegredo = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytesSegredo.length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException("noponto.jwt.segredo must have at least 32 bytes (256 bits)");
        }
        this.chaveAssinatura = Keys.hmacShaKeyFor(bytesSegredo);
        this.validade = validade;
        this.relogio = relogio;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = relogio.instant();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_TENANT_ID, usuario.getTenantId().toString())
                .claim(CLAIM_ROLE, usuario.getRole().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chaveAssinatura)
                .compact();
    }

    public UUID extrairTenantId(String token) {
        return UUID.fromString(extrairClaims(token).get(CLAIM_TENANT_ID, String.class));
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public PerfilUsuario extrairRole(String token) {
        return PerfilUsuario.valueOf(extrairClaims(token).get(CLAIM_ROLE, String.class));
    }

    public boolean tokenValido(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = extrairClaims(token);
            return claims.getSubject() != null
                    && claims.get(CLAIM_TENANT_ID, String.class) != null
                    && claims.get(CLAIM_ROLE, String.class) != null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chaveAssinatura)
                .clock(() -> Date.from(relogio.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
