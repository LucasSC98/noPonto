package me.lucascustodio.noponto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.OffsetTime;

/**
 * Janela de atendimento semanal. O offset de {@code horaInicio}/{@code horaFim} define o fuso
 * em que o dia da semana é interpretado (ex.: 08:00-03:00 = 08h no horário de Brasília).
 */
@Entity
@Table(name = "regra_disponibilidade")
@Getter
@Setter
@NoArgsConstructor
public class RegraDisponibilidade extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Convert(converter = ConversorDiaSemana.class)
    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek diaSemana;

    @JdbcType(TipoHorarioComFuso.class)
    @Column(name = "hora_inicio", nullable = false)
    private OffsetTime horaInicio;

    @JdbcType(TipoHorarioComFuso.class)
    @Column(name = "hora_fim", nullable = false)
    private OffsetTime horaFim;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;
}
