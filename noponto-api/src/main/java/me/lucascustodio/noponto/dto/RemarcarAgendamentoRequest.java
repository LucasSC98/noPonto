package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record RemarcarAgendamentoRequest(
        @NotNull(message = "O novo horário de início é obrigatório.") OffsetDateTime inicio) {
}
