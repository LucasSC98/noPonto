package me.lucascustodio.noponto.dto;

import me.lucascustodio.noponto.domain.ServicoItem;

import java.util.UUID;

/** Nunca expõe o tenant_id na API pública. */
public record ServicoItemResponse(UUID id, String nome, Integer duracaoMinutos, Boolean ativo) {

    public static ServicoItemResponse de(ServicoItem servico) {
        return new ServicoItemResponse(
                servico.getId(), servico.getNome(), servico.getDuracaoMinutos(), servico.getAtivo());
    }
}
