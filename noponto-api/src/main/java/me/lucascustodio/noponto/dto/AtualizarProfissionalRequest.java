package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarProfissionalRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 255) String email,
        @NotNull Boolean ativo,
        @Size(max = 80) String especialidade,
        @Size(max = 20) String telefone
) {
    public AtualizarProfissionalRequest(String nome, String email, Boolean ativo) {
        this(nome, email, ativo, null, null);
    }
}
