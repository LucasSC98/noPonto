package me.lucascustodio.noponto.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Requisição para criar ou atualizar um bloqueio do profissional.
 *
 * <p><strong>Tratamento de fuso horário:</strong> alinhado ao critério adotado na
 * {@code RegraDisponibilidade}, o cliente pode enviar {@code inicio} e {@code fim} em qualquer
 * {@link OffsetDateTime} ISO-8601 válido (por exemplo, no offset do estabelecimento {@code -03:00}
 * ou diretamente em UTC {@code Z}). A camada de serviço converte ambos os valores para UTC via
 * {@code withOffsetSameInstant(ZoneOffset.UTC)} antes da persistência em {@code TIMESTAMPTZ},
 * preservando o instante exato. O período segue a convenção de intervalo semiaberto
 * {@code [inicio, fim)} — bloqueios de dia inteiro correspondem a {@code 00:00} do dia inicial
 * até {@code 00:00} do dia seguinte ao final no fuso do estabelecimento.
 */
public record SalvarBloqueioRequest(
        @NotNull(message = "A data e hora de início são obrigatórias.") OffsetDateTime inicio,
        @NotNull(message = "A data e hora de fim são obrigatórias.") OffsetDateTime fim,
        String motivo,
        Boolean confirmarMesmoComConflito) {

    public boolean confirmadoMesmoComConflito() {
        return Boolean.TRUE.equals(confirmarMesmoComConflito);
    }
}
