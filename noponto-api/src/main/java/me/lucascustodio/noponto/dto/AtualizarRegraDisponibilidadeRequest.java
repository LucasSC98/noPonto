package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.OffsetTime;

public record AtualizarRegraDisponibilidadeRequest(
        @NotNull DayOfWeek diaSemana,
        @NotNull OffsetTime horaInicio,
        @NotNull OffsetTime horaFim,
        @NotNull Boolean ativo
) {}
