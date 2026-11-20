package me.lucascustodio.noponto.notificacao;

/**
 * Porta de saída do módulo de notificação por e-mail.
 * Os serviços de domínio dependem apenas desta interface ou de eventos publicados após o commit.
 */
public interface EnviadorEmail {

    void enviar(MensagemEmail mensagem);
}
