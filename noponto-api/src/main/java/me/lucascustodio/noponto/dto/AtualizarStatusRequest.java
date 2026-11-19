package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;
import me.lucascustodio.noponto.domain.StatusAgendamento;

public record AtualizarStatusRequest(@NotNull StatusAgendamento status) {
}
