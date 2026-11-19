package me.lucascustodio.noponto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    public static final int ANTECEDENCIA_MINIMA_CANCELAMENTO_PADRAO_MINUTOS = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 63)
    private String subdominio;

    @Column(name = "fuso_horario", nullable = false, length = 64)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(name = "antecedencia_minima_cancelamento_minutos", nullable = false)
    private Integer antecedenciaMinimaCancelamentoMinutos = ANTECEDENCIA_MINIMA_CANCELAMENTO_PADRAO_MINUTOS;

    @Column(name = "horario_abertura_padrao", nullable = false)
    private LocalTime horarioAberturaPadrao = LocalTime.of(8, 0);

    @Column(name = "horario_fechamento_padrao", nullable = false)
    private LocalTime horarioFechamentoPadrao = LocalTime.of(18, 0);

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void aoCriar() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
