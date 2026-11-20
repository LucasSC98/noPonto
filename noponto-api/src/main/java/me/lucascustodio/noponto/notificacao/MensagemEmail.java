package me.lucascustodio.noponto.notificacao;

import java.util.UUID;

/**
 * Mensagem de e-mail transacional.
 * {@code tenantId} e {@code agendamentoId} existem apenas para log seguro de diagnóstico;
 * o corpo e eventuais tokens nunca devem ser enviados para o log.
 */
public record MensagemEmail(
        UUID tenantId,
        UUID agendamentoId,
        String destinatario,
        String assunto,
        String corpoTexto,
        String corpoHtml) {
}
