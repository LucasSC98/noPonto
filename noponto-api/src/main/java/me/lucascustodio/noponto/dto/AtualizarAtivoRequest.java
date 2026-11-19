package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarAtivoRequest(@NotNull Boolean ativo) {}
