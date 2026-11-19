package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.ServicoItem;

import java.util.UUID;

public record ServicoVinculadoResponse(UUID id, String nome, Integer duracaoMinutos) {

    public static ServicoVinculadoResponse de(ServicoItem servico) {
        return new ServicoVinculadoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDuracaoMinutos()
        );
    }
}
