package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AtualizarServicoItemRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotNull @Positive Integer duracaoMinutos,
        @NotNull Boolean ativo
) {}
