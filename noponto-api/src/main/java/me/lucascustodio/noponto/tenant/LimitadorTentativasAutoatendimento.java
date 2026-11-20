package me.lucascustodio.noponto.tenant;

import jakarta.servlet.http.HttpServletRequest;
import me.lucascustodio.noponto.exception.MuitasTentativasExcecao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit simples por IP (janela deslizante em memória) para as rotas públicas
 * de autoatendimento por token, dificultando tentativas de adivinhação.
 */
@Component
public class LimitadorTentativasAutoatendimento {

    public static final int LIMITE_PADRAO_POR_MINUTO = 30;
    private static final Duration JANELA = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Deque<Instant>> tentativasPorIp = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final int limitePorJanela;

    @Autowired
    public LimitadorTentativasAutoatendimento(Clock relogio) {
        this(relogio, LIMITE_PADRAO_POR_MINUTO);
    }

    public LimitadorTentativasAutoatendimento(Clock relogio, int limitePorJanela) {
        this.relogio = relogio;
        this.limitePorJanela = limitePorJanela;
    }

    public void verificar(HttpServletRequest request) {
        String ip = extrairIp(request);
        verificarIp(ip);
    }

    public void verificarIp(String ip) {
        String chave = (ip == null || ip.isBlank()) ? "desconhecido" : ip.trim();
        Instant agora = Instant.now(relogio);
        Instant limiteInferior = agora.minus(JANELA);

        Deque<Instant> fila = tentativasPorIp.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst().isBefore(limiteInferior)) {
                fila.pollFirst();
            }
            if (fila.size() >= limitePorJanela) {
                throw new MuitasTentativasExcecao();
            }
            fila.addLast(agora);
        }
    }

    public void limpar() {
        tentativasPorIp.clear();
    }

    private static String extrairIp(HttpServletRequest request) {
        if (request == null) {
            return "desconhecido";
        }
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            int virgula = encaminhado.indexOf(',');
            return (virgula > 0 ? encaminhado.substring(0, virgula) : encaminhado).trim();
        }
        return request.getRemoteAddr();
    }
}
