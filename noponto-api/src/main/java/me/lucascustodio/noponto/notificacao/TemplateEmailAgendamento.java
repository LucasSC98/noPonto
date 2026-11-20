package me.lucascustodio.noponto.notificacao;

import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Monta e-mails transacionais (HTML simples + texto puro) em português do Brasil,
 * convertendo os instantes UTC do agendamento para o fuso configurado no tenant.
 */
@Component
public class TemplateEmailAgendamento {

    private static final Locale LOCALE_BR = Locale.of("pt", "BR");
    private static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATADOR_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_BR);
    private static final DateTimeFormatter FORMATADOR_HORA =
            DateTimeFormatter.ofPattern("HH:mm", LOCALE_BR);

    public MensagemEmail criarConfirmacao(DadosEmailAgendamento dados) {
        String periodo = formatarPeriodoNoFusoDoTenant(
                dados.inicioUtc(), dados.fimUtc(), dados.fusoHorarioTenant());
        String assunto = "Confirmação de agendamento — " + dados.nomeEstabelecimento();

        String texto = """
                Olá, %s!

                Seu agendamento em %s foi registrado com sucesso.

                Resumo do agendamento:
                - Estabelecimento: %s
                - Serviço: %s
                - Profissional: %s
                - Data e horário: %s

                Para consultar, remarcar ou cancelar seu agendamento, acesse:
                %s
                """.formatted(
                dados.nomeCliente(),
                dados.nomeEstabelecimento(),
                dados.nomeEstabelecimento(),
                dados.nomeServico(),
                dados.nomeProfissional(),
                periodo,
                dados.linkAutoatendimento());

        String html = montarHtml(
                "Agendamento confirmado",
                "Olá, <strong>" + escaparHtml(dados.nomeCliente()) + "</strong>! Seu horário em <strong>"
                        + escaparHtml(dados.nomeEstabelecimento()) + "</strong> foi reservado.",
                dados,
                periodo,
                null,
                true);

        return new MensagemEmail(
                dados.tenantId(),
                dados.agendamentoId(),
                dados.emailCliente(),
                assunto,
                texto,
                html);
    }

    public MensagemEmail criarCancelamento(DadosEmailAgendamento dados) {
        String periodo = formatarPeriodoNoFusoDoTenant(
                dados.inicioUtc(), dados.fimUtc(), dados.fusoHorarioTenant());
        String assunto = "Agendamento cancelado — " + dados.nomeEstabelecimento();

        String texto = """
                Olá, %s!

                Confirmamos o cancelamento do seu agendamento em %s.

                Detalhes do horário cancelado:
                - Estabelecimento: %s
                - Serviço: %s
                - Profissional: %s
                - Data e horário: %s
                """.formatted(
                dados.nomeCliente(),
                dados.nomeEstabelecimento(),
                dados.nomeEstabelecimento(),
                dados.nomeServico(),
                dados.nomeProfissional(),
                periodo);

        String html = montarHtml(
                "Agendamento cancelado",
                "Olá, <strong>" + escaparHtml(dados.nomeCliente())
                        + "</strong>. O seu agendamento em <strong>"
                        + escaparHtml(dados.nomeEstabelecimento()) + "</strong> foi cancelado.",
                dados,
                periodo,
                null,
                false);

        return new MensagemEmail(
                dados.tenantId(),
                dados.agendamentoId(),
                dados.emailCliente(),
                assunto,
                texto,
                html);
    }

    public MensagemEmail criarRemarcacao(DadosEmailAgendamento dados,
                                         OffsetDateTime inicioAnteriorUtc,
                                         OffsetDateTime fimAnteriorUtc) {
        String novoPeriodo = formatarPeriodoNoFusoDoTenant(
                dados.inicioUtc(), dados.fimUtc(), dados.fusoHorarioTenant());
        String periodoAnterior = formatarPeriodoNoFusoDoTenant(
                inicioAnteriorUtc, fimAnteriorUtc, dados.fusoHorarioTenant());
        String assunto = "Agendamento remarcado — " + dados.nomeEstabelecimento();

        String texto = """
                Olá, %s!

                Seu agendamento em %s foi remarcado com sucesso.

                Novo horário:
                - Estabelecimento: %s
                - Serviço: %s
                - Profissional: %s
                - Data e horário: %s
                - Horário anterior: %s

                Para consultar, remarcar ou cancelar seu agendamento, acesse:
                %s
                """.formatted(
                dados.nomeCliente(),
                dados.nomeEstabelecimento(),
                dados.nomeEstabelecimento(),
                dados.nomeServico(),
                dados.nomeProfissional(),
                novoPeriodo,
                periodoAnterior,
                dados.linkAutoatendimento());

        String html = montarHtml(
                "Agendamento remarcado",
                "Olá, <strong>" + escaparHtml(dados.nomeCliente())
                        + "</strong>! Seu atendimento em <strong>"
                        + escaparHtml(dados.nomeEstabelecimento()) + "</strong> foi atualizado para um novo horário.",
                dados,
                novoPeriodo,
                periodoAnterior,
                true);

        return new MensagemEmail(
                dados.tenantId(),
                dados.agendamentoId(),
                dados.emailCliente(),
                assunto,
                texto,
                html);
    }

    public MensagemEmail criarLembrete24h(DadosEmailAgendamento dados) {
        String periodo = formatarPeriodoNoFusoDoTenant(
                dados.inicioUtc(), dados.fimUtc(), dados.fusoHorarioTenant());
        String assunto = "Lembrete do seu agendamento — " + dados.nomeEstabelecimento();

        String texto = """
                Olá, %s!

                Este é um lembrete do seu agendamento em %s nas próximas 24 horas.

                Resumo do agendamento:
                - Estabelecimento: %s
                - Serviço: %s
                - Profissional: %s
                - Data e horário: %s

                Caso precise remarcar ou cancelar, acesse:
                %s
                """.formatted(
                dados.nomeCliente(),
                dados.nomeEstabelecimento(),
                dados.nomeEstabelecimento(),
                dados.nomeServico(),
                dados.nomeProfissional(),
                periodo,
                dados.linkAutoatendimento());

        String html = montarHtml(
                "Lembrete de agendamento",
                "Olá, <strong>" + escaparHtml(dados.nomeCliente())
                        + "</strong>! Lembramos que o seu horário em <strong>"
                        + escaparHtml(dados.nomeEstabelecimento()) + "</strong> está próximo.",
                dados,
                periodo,
                null,
                dados.linkAutoatendimento() != null && !dados.linkAutoatendimento().isBlank());

        return new MensagemEmail(
                dados.tenantId(),
                dados.agendamentoId(),
                dados.emailCliente(),
                assunto,
                texto,
                html);
    }

    public String formatarPeriodoNoFusoDoTenant(OffsetDateTime inicioUtc,
                                                OffsetDateTime fimUtc,
                                                String fusoHorarioTenant) {
        ZoneId fuso = resolverFuso(fusoHorarioTenant);
        ZonedDateTime inicioLocal = inicioUtc.atZoneSameInstant(fuso);
        ZonedDateTime fimLocal = fimUtc.atZoneSameInstant(fuso);
        return "%s às %s (até %s)".formatted(
                FORMATADOR_DATA.format(inicioLocal),
                FORMATADOR_HORA.format(inicioLocal),
                FORMATADOR_HORA.format(fimLocal));
    }

    private static ZoneId resolverFuso(String fusoHorarioTenant) {
        if (fusoHorarioTenant == null || fusoHorarioTenant.isBlank()) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(fusoHorarioTenant.trim());
        } catch (DateTimeException excecao) {
            return FUSO_PADRAO;
        }
    }

    private static String montarHtml(String titulo,
                                     String introducaoHtml,
                                     DadosEmailAgendamento dados,
                                     String periodoFormatado,
                                     String periodoAnteriorFormatado,
                                     boolean exibirLinkAutoatendimento) {
        String linhaAnterior = periodoAnteriorFormatado != null
                ? "<li><strong>Horário anterior:</strong> " + escaparHtml(periodoAnteriorFormatado) + "</li>"
                : "";
        String blocoLink = exibirLinkAutoatendimento
                && dados.linkAutoatendimento() != null
                && !dados.linkAutoatendimento().isBlank()
                ? """
                <p style="margin-top:16px;">
                  <a href="%s" style="display:inline-block;padding:10px 16px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600;">
                    Gerenciar agendamento
                  </a>
                </p>
                <p style="font-size:12px;color:#64748b;">Link direto: %s</p>
                """.formatted(
                escaparHtml(dados.linkAutoatendimento()),
                escaparHtml(dados.linkAutoatendimento()))
                : "";

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <body style="font-family:Arial,sans-serif;color:#0f172a;line-height:1.5;">
                  <div style="max-width:560px;margin:0 auto;padding:20px;border:1px solid #e2e8f0;border-radius:8px;">
                    <h2 style="margin-top:0;color:#2563eb;">%s</h2>
                    <p>%s</p>
                    <ul style="padding-left:18px;">
                      <li><strong>Estabelecimento:</strong> %s</li>
                      <li><strong>Serviço:</strong> %s</li>
                      <li><strong>Profissional:</strong> %s</li>
                      <li><strong>Data e horário:</strong> %s</li>
                      %s
                    </ul>
                    %s
                  </div>
                </body>
                </html>
                """.formatted(
                escaparHtml(titulo),
                introducaoHtml,
                escaparHtml(dados.nomeEstabelecimento()),
                escaparHtml(dados.nomeServico()),
                escaparHtml(dados.nomeProfissional()),
                escaparHtml(periodoFormatado),
                linhaAnterior,
                blocoLink);
    }

    private static String escaparHtml(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
