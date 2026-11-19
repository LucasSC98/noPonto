package me.lucascustodio.noponto.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErroResponse(
        int status,
        String mensagem,
        OffsetDateTime momento,
        Map<String, String> campos) {
}
