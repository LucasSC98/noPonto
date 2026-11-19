package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Profissional;

import java.util.UUID;

/** Dados públicos do profissional para a página de agendamento. Nunca expõe telefone, e-mail ou tenant_id. */
public record ProfissionalPublicoResponse(UUID id, String nome, String especialidade) {

    public static ProfissionalPublicoResponse de(Profissional profissional) {
        return new ProfissionalPublicoResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEspecialidade()
        );
    }
}
