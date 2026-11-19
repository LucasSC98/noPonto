package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarProfissionalRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 255) String email,
        @NotNull Boolean ativo
) {}
