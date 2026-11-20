package me.lucascustodio.noponto.notificacao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OuvinteNotificacaoEmail {

    private final EnviadorEmail enviadorEmail;

    public OuvinteNotificacaoEmail(EnviadorEmail enviadorEmail) {
        this.enviadorEmail = enviadorEmail;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarAposCommit(EventoEmailAgendamento evento) {
        MensagemEmail mensagem = evento.mensagem();
        if (mensagem == null || mensagem.destinatario() == null || mensagem.destinatario().isBlank()) {
            return;
        }
        try {
            enviadorEmail.enviar(mensagem);
        } catch (Exception excecao) {
            // Nunca registra corpo do e-mail, destinatário nem token de autoatendimento no log.
            log.error("Falha ao enviar e-mail de agendamento: tenantId={}, agendamentoId={}, causa={}",
                    mensagem.tenantId(), mensagem.agendamentoId(), excecao.getClass().getSimpleName());
        }
    }
}
