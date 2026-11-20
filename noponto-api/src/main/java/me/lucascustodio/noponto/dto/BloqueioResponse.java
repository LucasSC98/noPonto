package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Bloqueio;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Nunca expõe o tenant_id. Datas sempre normalizadas em UTC. */
public record BloqueioResponse(
        UUID id,
        UUID profissionalId,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String motivo) {

    public static BloqueioResponse de(Bloqueio bloqueio) {
        return new BloqueioResponse(
                bloqueio.getId(),
                bloqueio.getProfissional().getId(),
                bloqueio.getInicio().withOffsetSameInstant(ZoneOffset.UTC),
                bloqueio.getFim().withOffsetSameInstant(ZoneOffset.UTC),
                bloqueio.getMotivo());
    }
}
