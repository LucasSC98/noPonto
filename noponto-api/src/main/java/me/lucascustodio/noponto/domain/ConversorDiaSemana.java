package me.lucascustodio.noponto.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;

/**
 * Persiste o dia da semana no padrão ISO-8601 da migration: 1 = segunda-feira ... 7 = domingo.
 */
@Converter
public class ConversorDiaSemana implements AttributeConverter<DayOfWeek, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DayOfWeek diaSemana) {
        return diaSemana == null ? null : diaSemana.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Integer valor) {
        return valor == null ? null : DayOfWeek.of(valor);
    }
}
