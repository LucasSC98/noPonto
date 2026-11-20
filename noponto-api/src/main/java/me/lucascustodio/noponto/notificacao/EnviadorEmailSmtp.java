package me.lucascustodio.noponto.notificacao;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class EnviadorEmailSmtp implements EnviadorEmail {

    private final JavaMailSender mailSender;
    private final String remetente;

    public EnviadorEmailSmtp(JavaMailSender mailSender,
                             @Value("${noponto.email.remetente}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente);
            helper.setTo(mensagem.destinatario());
            helper.setSubject(mensagem.assunto());
            helper.setText(mensagem.corpoTexto(), mensagem.corpoHtml());
            mailSender.send(mimeMessage);
        } catch (MessagingException excecao) {
            throw new MailSendException("Falha ao montar mensagem MIME de e-mail.", excecao);
        }
    }
}
