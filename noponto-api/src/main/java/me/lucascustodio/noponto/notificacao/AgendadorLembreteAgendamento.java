package me.lucascustodio.noponto.notificacao;

import lombok.extern.slf4j.Slf4j;
import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.service.TenantService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job agendado que envia lembrete por e-mail para agendamentos PENDENTE ou CONFIRMADO
 * que ocorrem em cerca de 24 horas (janela [agora + 23h, agora + 25h]).
 *
 * <p>Idempotência: antes de disparar o e-mail de cada agendamento, executa um
 * {@code UPDATE ... WHERE id = :id AND lembrete_enviado_em IS NULL} condicional e atômico
 * em transação própria. Apenas a execução/instância que obtiver {@code 1} linha afetada
 * realiza o envio, impedindo lembretes duplicados.
 */
@Slf4j
@Service
public class AgendadorLembreteAgendamento {

    private static final int HORAS_INICIO_JANELA = 23;
    private static final int HORAS_FIM_JANELA = 25;

    private final AgendamentoRepository agendamentoRepository;
    private final TenantService tenantService;
    private final TemplateEmailAgendamento templateEmailAgendamento;
    private final EnviadorEmail enviadorEmail;
    private final TransactionTemplate transactionTemplate;
    private final Clock relogio;

    public AgendadorLembreteAgendamento(AgendamentoRepository agendamentoRepository,
                                        TenantService tenantService,
                                        TemplateEmailAgendamento templateEmailAgendamento,
                                        EnviadorEmail enviadorEmail,
                                        TransactionTemplate transactionTemplate,
                                        Clock relogio) {
        this.agendamentoRepository = agendamentoRepository;
        this.tenantService = tenantService;
        this.templateEmailAgendamento = templateEmailAgendamento;
        this.enviadorEmail = enviadorEmail;
        this.transactionTemplate = transactionTemplate;
        this.relogio = relogio;
    }

    @Scheduled(
            initialDelayString = "${noponto.lembrete.atraso-inicial-ms:60000}",
            fixedDelayString = "${noponto.lembrete.intervalo-ms:900000}")
    public int processarLembretes24h() {
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        OffsetDateTime janelaInicio = agora.plusHours(HORAS_INICIO_JANELA);
        OffsetDateTime janelaFim = agora.plusHours(HORAS_FIM_JANELA);

        List<Agendamento> candidatos = agendamentoRepository.buscarPendentesDeLembrete(
                StatusAgendamento.ocupamHorario(), janelaInicio, janelaFim);

        int enviados = 0;
        for (Agendamento agendamento : candidatos) {
            if (agendamento.getEmailCliente() == null || agendamento.getEmailCliente().isBlank()) {
                continue;
            }
            if (reservarAtomicamente(agendamento, agora)) {
                enviarLembreteSeguro(agendamento);
                enviados++;
            }
        }
        return enviados;
    }

    private boolean reservarAtomicamente(Agendamento agendamento, OffsetDateTime agora) {
        Integer atualizados = transactionTemplate.execute(status ->
                agendamentoRepository.reservarEnvioLembrete(
                        agendamento.getId(),
                        agendamento.getTenantId(),
                        StatusAgendamento.ocupamHorario(),
                        agora));
        return atualizados != null && atualizados == 1;
    }

    private void enviarLembreteSeguro(Agendamento agendamento) {
        try {
            Tenant tenant = buscarTenantSeguro(agendamento);
            String nomeEstabelecimento = tenant != null && tenant.getNome() != null
                    ? tenant.getNome()
                    : "NoPonto";
            String fuso = tenant != null && tenant.getFusoHorario() != null
                    ? tenant.getFusoHorario()
                    : "America/Sao_Paulo";

            DadosEmailAgendamento dados = new DadosEmailAgendamento(
                    agendamento.getTenantId(),
                    agendamento.getId(),
                    nomeEstabelecimento,
                    fuso,
                    agendamento.getNomeCliente(),
                    agendamento.getEmailCliente(),
                    agendamento.getProfissional().getNome(),
                    agendamento.getServicoItem().getNome(),
                    agendamento.getInicio(),
                    agendamento.getFim(),
                    null);
            MensagemEmail mensagem = templateEmailAgendamento.criarLembrete24h(dados);
            enviadorEmail.enviar(mensagem);
        } catch (Exception excecao) {
            log.error("Falha ao enviar lembrete 24h: tenantId={}, agendamentoId={}, causa={}",
                    agendamento.getTenantId(), agendamento.getId(), excecao.getClass().getSimpleName());
        }
    }

    private Tenant buscarTenantSeguro(Agendamento agendamento) {
        try {
            return tenantService.buscarPorId(agendamento.getTenantId());
        } catch (Exception excecao) {
            return null;
        }
    }
}
