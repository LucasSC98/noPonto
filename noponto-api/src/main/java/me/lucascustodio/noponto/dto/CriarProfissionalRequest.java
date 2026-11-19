package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarProfissionalRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 255) String email,
        @Size(max = 80) String especialidade,
        @Size(max = 20) String telefone
) {
    public CriarProfissionalRequest(String nome, String email) {
        this(nome, email, null, null);
    }
}
