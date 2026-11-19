package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.Profissional;

import java.util.List;
import java.util.UUID;

public record ProfissionalResponse(
        UUID id,
        String nome,
        String email,
        String especialidade,
        String telefone,
        Boolean ativo,
        boolean temDisponibilidade,
        long quantidadeServicosAtivos,
        long agendamentosSemana,
        List<ServicoVinculadoResponse> servicosAtivos
) {

    public static ProfissionalResponse de(Profissional profissional) {
        return de(profissional, false, 0L, List.of());
    }

    public static ProfissionalResponse de(Profissional profissional,
                                          boolean temDisponibilidade,
                                          long agendamentosSemana,
                                          List<ServicoVinculadoResponse> servicosAtivos) {
        List<ServicoVinculadoResponse> lista = servicosAtivos == null ? List.of() : List.copyOf(servicosAtivos);
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getEspecialidade(),
                profissional.getTelefone(),
                profissional.getAtivo(),
                temDisponibilidade,
                lista.size(),
                agendamentosSemana,
                lista
        );
    }
}
