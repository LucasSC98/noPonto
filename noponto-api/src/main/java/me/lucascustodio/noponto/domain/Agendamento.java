package me.lucascustodio.noponto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Ocupa o intervalo {@code [inicio, fim)} do profissional.
 */
@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
public class Agendamento extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_servico_id", nullable = false)
    private ServicoItem servicoItem;

    @Column(name = "nome_cliente", nullable = false, length = 150)
    private String nomeCliente;

    @Column(name = "email_cliente", length = 255)
    private String emailCliente;

    @Column(name = "horario_inicio", nullable = false)
    private OffsetDateTime inicio;

    @Column(name = "horario_fim", nullable = false)
    private OffsetDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.PENDENTE;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;
}
