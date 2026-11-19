package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CriarServicoItemRequest(
        @NotNull UUID profissionalId,
        @NotBlank @Size(max = 150) String nome,
        @NotNull @Positive Integer duracaoMinutos
) {}
