package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.UUID;

public record CriarRegraDisponibilidadeRequest(
        @NotNull UUID profissionalId,
        @NotNull DayOfWeek diaSemana,
        @NotNull OffsetTime horaInicio,
        @NotNull OffsetTime horaFim
) {}
