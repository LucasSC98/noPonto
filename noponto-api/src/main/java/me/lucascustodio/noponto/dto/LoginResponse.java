package me.lucascustodio.noponto.dto;

public record LoginResponse(String token, String tipo, long expiraEmSegundos) {

    public static final String TIPO_BEARER = "Bearer";
}
