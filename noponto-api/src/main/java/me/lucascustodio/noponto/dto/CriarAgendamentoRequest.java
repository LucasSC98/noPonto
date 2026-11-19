package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CriarAgendamentoRequest(
        @NotNull UUID profissionalId,
        @NotNull UUID servicoItemId,
        @NotBlank @Size(max = 150) String nomeCliente,
        @NotBlank @Email @Size(max = 255) String emailCliente,
        @NotNull OffsetDateTime inicio) {
}
