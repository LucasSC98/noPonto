package me.lucascustodio.noponto.notificacao;

/**
 * Evento publicado dentro de transações de agendamento e processado apenas após o commit.
 */
public record EventoEmailAgendamento(MensagemEmail mensagem) {
}
