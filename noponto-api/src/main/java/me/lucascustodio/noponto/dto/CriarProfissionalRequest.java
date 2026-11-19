package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarProfissionalRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 255) String email
) {}
