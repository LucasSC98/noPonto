package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Profissional;

import java.util.UUID;

public record ProfissionalResponse(
        UUID id,
        String nome,
        String email,
        Boolean ativo,
        boolean temDisponibilidade
) {

    public static ProfissionalResponse de(Profissional profissional) {
        return de(profissional, false);
    }

    public static ProfissionalResponse de(Profissional profissional, boolean temDisponibilidade) {
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getAtivo(),
                temDisponibilidade
        );
    }
}
