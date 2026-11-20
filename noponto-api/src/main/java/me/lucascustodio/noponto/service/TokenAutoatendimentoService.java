package me.lucascustodio.noponto.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Gera tokens aleatórios de autoatendimento (32 bytes em Base64URL sem padding)
 * e calcula o hash SHA-256 em hexadecimal para persistência segura no banco.
 * O valor puro do token nunca é armazenado.
 */
@Component
public class TokenAutoatendimentoService {

    private static final int TAMANHO_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public record TokenGerado(String tokenPuro, String hashSha256) {
    }

    public TokenGerado gerar() {
        byte[] bytes = new byte[TAMANHO_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String tokenPuro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenGerado(tokenPuro, calcularHash(tokenPuro));
    }

    public String calcularHash(String tokenPuro) {
        if (tokenPuro == null || tokenPuro.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(tokenPuro.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível na JVM.", excecao);
        }
    }
}
