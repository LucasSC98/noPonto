package me.lucascustodio.noponto.repository;

import java.util.UUID;

public record ServicoVinculadoPorProfissional(
        UUID profissionalId,
        UUID id,
        String nome,
        Integer duracaoMinutos
) {
}
