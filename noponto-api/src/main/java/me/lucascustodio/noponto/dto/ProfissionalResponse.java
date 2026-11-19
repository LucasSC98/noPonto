package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Profissional;

import java.util.UUID;

public record ProfissionalResponse(
        UUID id,
        String nome,
        String email,
        Boolean ativo,
        boolean temDisponibilidade,
        long quantidadeServicosAtivos,
        long agendamentosSemana
) {

    public static ProfissionalResponse de(Profissional profissional) {
        return de(profissional, false, 0L, 0L);
    }

    public static ProfissionalResponse de(Profissional profissional,
                                          boolean temDisponibilidade,
                                          long quantidadeServicosAtivos,
                                          long agendamentosSemana) {
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getAtivo(),
                temDisponibilidade,
                quantidadeServicosAtivos,
                agendamentosSemana
        );
    }
}
