package me.lucascustodio.noponto.notificacao;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacaoEmailTest {

    @Test
    void deve_formatarTemplateEmPortuguesNoFusoDoTenant_quando_datasEmUtc() {
        TemplateEmailAgendamento template = new TemplateEmailAgendamento();
        DadosEmailAgendamento dados = new DadosEmailAgendamento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Clínica Teste",
                "America/Sao_Paulo",
                "Maria Silva",
                "maria@cliente.com",
                "Dr. João Silva",
                "Consulta Geral",
                OffsetDateTime.parse("2026-11-25T12:00:00Z"),
                OffsetDateTime.parse("2026-11-25T12:30:00Z"),
                "http://localhost:5173/agendamento/token-exemplo");

        MensagemEmail mensagem = template.criarConfirmacao(dados);

        assertThat(mensagem.destinatario()).isEqualTo("maria@cliente.com");
        assertThat(mensagem.assunto()).isEqualTo("Confirmação de agendamento — Clínica Teste");
        assertThat(mensagem.corpoTexto())
                .contains("Clínica Teste")
                .contains("Consulta Geral")
                .contains("Dr. João Silva")
                .contains("25/11/2026 às 09:00 (até 09:30)")
                .contains("http://localhost:5173/agendamento/token-exemplo");
        assertThat(mensagem.corpoHtml())
                .contains("Clínica Teste")
                .contains("Consulta Geral")
                .contains("Dr. João Silva")
                .contains("25/11/2026 às 09:00 (até 09:30)")
                .contains("http://localhost:5173/agendamento/token-exemplo");
    }

    @Test
    void deve_naoPropagarExcecao_quando_envioDeEmailFalhaNoOuvinte() {
        EnviadorEmail enviador = mock(EnviadorEmail.class);
        doThrow(new MailSendException("SMTP indisponível")).when(enviador).enviar(any());
        OuvinteNotificacaoEmail ouvinte = new OuvinteNotificacaoEmail(enviador);

        MensagemEmail mensagem = new MensagemEmail(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "maria@cliente.com",
                "Assunto",
                "Texto",
                "<p>HTML</p>");

        assertThatCode(() -> ouvinte.enviarAposCommit(new EventoEmailAgendamento(mensagem)))
                .doesNotThrowAnyException();
        verify(enviador).enviar(mensagem);
    }

    @Test
    void deve_enviarMimeMessageViaJavaMailSender_quando_enviadorSmtpAcionado() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EnviadorEmailSmtp enviador = new EnviadorEmailSmtp(mailSender, "nao-responda@noponto.local");
        MensagemEmail mensagem = new MensagemEmail(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "cliente@teste.com",
                "Confirmação",
                "Texto puro",
                "<p>HTML</p>");

        enviador.enviar(mensagem);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Confirmação");
    }
}
