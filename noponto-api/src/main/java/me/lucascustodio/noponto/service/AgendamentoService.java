package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.dto.AgendamentoAutoatendimentoResponse;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.dto.RemarcarAgendamentoRequest;
import me.lucascustodio.noponto.exception.HorarioIndisponivelExcecao;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.notificacao.DadosEmailAgendamento;
import me.lucascustodio.noponto.notificacao.EventoEmailAgendamento;
import me.lucascustodio.noponto.notificacao.MensagemEmail;
import me.lucascustodio.noponto.notificacao.TemplateEmailAgendamento;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AgendamentoService {

    private static final String MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO =
            "Agendamento não encontrado ou link de autoatendimento inválido.";

    private final AgendamentoRepository agendamentoRepository;
    private final ProfissionalService profissionalService;
    private final SlotService slotService;
    private final TenantService tenantService;
    private final Clock relogio;
    private final TokenAutoatendimentoService tokenAutoatendimentoService;
    private final TemplateEmailAgendamento templateEmailAgendamento;
    private final ApplicationEventPublisher eventPublisher;
    private final String urlBaseFrontend;

    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                              ProfissionalService profissionalService,
                              SlotService slotService,
                              TenantService tenantService,
                              Clock relogio) {
        this(agendamentoRepository,
                profissionalService,
                slotService,
                tenantService,
                relogio,
                new TokenAutoatendimentoService(),
                new TemplateEmailAgendamento(),
                evento -> {},
                "http://localhost:5173");
    }

    @Autowired
    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                              ProfissionalService profissionalService,
                              SlotService slotService,
                              TenantService tenantService,
                              Clock relogio,
                              TokenAutoatendimentoService tokenAutoatendimentoService,
                              TemplateEmailAgendamento templateEmailAgendamento,
                              ApplicationEventPublisher eventPublisher,
                              @Value("${noponto.frontend.url-base:http://localhost:5173}") String urlBaseFrontend) {
        this.agendamentoRepository = agendamentoRepository;
        this.profissionalService = profissionalService;
        this.slotService = slotService;
        this.tenantService = tenantService;
        this.relogio = relogio;
        this.tokenAutoatendimentoService = tokenAutoatendimentoService;
        this.templateEmailAgendamento = templateEmailAgendamento;
        this.eventPublisher = eventPublisher;
        this.urlBaseFrontend = urlBaseFrontend;
    }

    /**
     * Cria o agendamento de forma transacional. Primeiro trava (SELECT FOR UPDATE) a linha do profissional,
     * o que serializa criações concorrentes para o mesmo profissional; só então recalcula se o slot ainda
     * está livre. Gera um token aleatório de 32 bytes (Base64URL), persiste apenas seu hash SHA-256 e
     * dispara o e-mail de confirmação após o commit da transação.
     */
    @Transactional
    public AgendamentoResponse criar(CriarAgendamentoRequest request, UUID tenantId) {
        return criarComToken(request, tenantId).agendamento();
    }

    public record AgendamentoCriadoComToken(AgendamentoResponse agendamento, String tokenPuro, String linkGerenciamento) {
    }

    @Transactional
    public AgendamentoCriadoComToken criarComToken(CriarAgendamentoRequest request, UUID tenantId) {
        Profissional profissional = profissionalService.buscarAtivoComBloqueio(tenantId, request.profissionalId());
        ServicoItem servico = profissionalService
                .buscarServicoParaAgendamento(tenantId, profissional.getId(), request.servicoItemId());

        OffsetDateTime inicio = request.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        if (!slotService.slotDisponivel(profissional.getId(), inicio, tenantId, servico.getId())) {
            throw new HorarioIndisponivelExcecao();
        }

        TokenAutoatendimentoService.TokenGerado tokenGerado = tokenAutoatendimentoService.gerar();

        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setProfissional(profissional);
        agendamento.setServicoItem(servico);
        agendamento.setNomeCliente(request.nomeCliente().trim());
        agendamento.setEmailCliente(request.emailCliente().trim());
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plus(Duration.ofMinutes(servico.getDuracaoMinutos())));
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        agendamento.setTokenGerenciamentoHash(tokenGerado.hashSha256());
        agendamento.setCanceladoPeloCliente(Boolean.FALSE);
        agendamento.setRemarcado(Boolean.FALSE);

        Agendamento salvo = agendamentoRepository.save(agendamento);
        String link = montarLinkAutoatendimento(tokenGerado.tokenPuro());
        publicarEmailConfirmacao(salvo, tenantId, link);

        return new AgendamentoCriadoComToken(AgendamentoResponse.de(salvo), tokenGerado.tokenPuro(), link);
    }

    @Transactional(readOnly = true)
    public AgendamentoAutoatendimentoResponse consultarPorToken(String tokenPuro) {
        Agendamento agendamento = buscarPorTokenValido(tokenPuro);
        Tenant tenant = buscarTenantSeguro(agendamento.getTenantId());
        return AgendamentoAutoatendimentoResponse.de(agendamento, tenant);
    }

    @Transactional(readOnly = true)
    public List<OffsetDateTime> listarSlotsParaRemarcacao(String tokenPuro, LocalDate data) {
        Agendamento agendamento = buscarPorTokenValido(tokenPuro);
        return slotService.calcularSlotsDisponiveis(
                agendamento.getProfissional().getId(),
                data,
                agendamento.getTenantId(),
                agendamento.getServicoItem().getId(),
                agendamento.getId());
    }

    /**
     * Cancelamento pelo cliente via token de autoatendimento.
     * Só é permitido para PENDENTE ou CONFIRMADO e respeitando a antecedência mínima do tenant.
     * Profissional ou serviço inativo não impede o cancelamento.
     */
    @Transactional
    public AgendamentoAutoatendimentoResponse cancelarPorToken(String tokenPuro) {
        Agendamento agendamento = buscarPorTokenValido(tokenPuro);
        UUID tenantId = agendamento.getTenantId();
        OffsetDateTime agora = OffsetDateTime.now(relogio);

        validarAntecedenciaCancelamento(agendamento, tenantId, agora);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        agendamento.setCanceladoEm(agora);
        agendamento.setCanceladoPeloCliente(Boolean.TRUE);

        Agendamento salvo = agendamentoRepository.save(agendamento);
        publicarEmailCancelamento(salvo, tenantId);

        return AgendamentoAutoatendimentoResponse.de(salvo, buscarTenantSeguro(tenantId));
    }

    /**
     * Remarcação transacional pelo cliente via token de autoatendimento.
     * Exige antecedência mínima em relação ao horário ATUAL, profissional e serviço ativos,
     * adquire lock pessimista (SELECT FOR UPDATE) na linha do profissional e revalida o novo slot
     * desconsiderando o próprio agendamento antes de atualizar o mesmo registro (mantendo id e token).
     */
    @Transactional
    public AgendamentoAutoatendimentoResponse remarcarPorToken(String tokenPuro,
                                                               RemarcarAgendamentoRequest requisicao) {
        Agendamento agendamento = buscarPorTokenValido(tokenPuro);
        if (requisicao == null || requisicao.inicio() == null) {
            throw new RegraNegocioExcecao("Informe o novo horário para remarcação.");
        }

        UUID tenantId = agendamento.getTenantId();
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        validarAntecedenciaCancelamento(agendamento, tenantId, agora);

        Profissional profissional = profissionalService.buscarAtivoComBloqueio(
                tenantId, agendamento.getProfissional().getId());
        ServicoItem servico = profissionalService.buscarServicoParaAgendamento(
                tenantId, profissional.getId(), agendamento.getServicoItem().getId());

        // Revalida estado do próprio agendamento após adquirir o lock do profissional
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
                || agendamento.getStatus() == StatusAgendamento.CONCLUIDO
                || !agendamento.getFim().isAfter(agora)) {
            throw new RecursoNaoEncontradoExcecao(MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO);
        }

        OffsetDateTime novoInicio = requisicao.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        if (!slotService.slotDisponivel(
                profissional.getId(), novoInicio, tenantId, servico.getId(), agendamento.getId())) {
            throw new HorarioIndisponivelExcecao();
        }

        OffsetDateTime inicioAnterior = agendamento.getInicio();
        OffsetDateTime fimAnterior = agendamento.getFim();

        agendamento.setInicio(novoInicio);
        agendamento.setFim(novoInicio.plus(Duration.ofMinutes(servico.getDuracaoMinutos())));
        agendamento.setRemarcado(Boolean.TRUE);

        Agendamento salvo = agendamentoRepository.save(agendamento);
        publicarEmailRemarcacao(
                salvo,
                tenantId,
                montarLinkAutoatendimento(tokenPuro.trim()),
                inicioAnterior,
                fimAnterior);

        return AgendamentoAutoatendimentoResponse.de(salvo, buscarTenantSeguro(tenantId));
    }

    /**
     * Localiza um agendamento ativo (PENDENTE ou CONFIRMADO) cujo token de autoatendimento
     * corresponda ao hash SHA-256 e ainda não tenha expirado (fim > agora).
     * Qualquer caso inválido, expirado, concluído, cancelado ou inexistente lança o mesmo 404 genérico.
     */
    @Transactional(readOnly = true)
    public Agendamento buscarPorTokenValido(String tokenPuro) {
        if (tokenPuro == null || tokenPuro.isBlank()) {
            throw new RecursoNaoEncontradoExcecao(MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO);
        }
        String hash = tokenAutoatendimentoService.calcularHash(tokenPuro);
        Agendamento agendamento = agendamentoRepository.buscarPorTokenGerenciamentoHash(hash)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao(MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
                || agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RecursoNaoEncontradoExcecao(MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO);
        }

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        if (!agendamento.getFim().isAfter(agora)) {
            throw new RecursoNaoEncontradoExcecao(MENSAGEM_TOKEN_INVALIDO_OU_EXPIRADO);
        }

        return agendamento;
    }

    public String montarLinkAutoatendimento(String tokenPuro) {
        String base = (urlBaseFrontend == null || urlBaseFrontend.isBlank())
                ? "http://localhost:5173"
                : urlBaseFrontend.replaceAll("/+$", "");
        return base + "/agendamento/" + tokenPuro;
    }

    private void publicarEmailConfirmacao(Agendamento agendamento, UUID tenantId, String linkAutoatendimento) {
        if (agendamento.getEmailCliente() == null || agendamento.getEmailCliente().isBlank()) {
            return;
        }
        DadosEmailAgendamento dados = montarDadosEmail(agendamento, tenantId, linkAutoatendimento);
        MensagemEmail mensagem = templateEmailAgendamento.criarConfirmacao(dados);
        eventPublisher.publishEvent(new EventoEmailAgendamento(mensagem));
    }

    private void publicarEmailCancelamento(Agendamento agendamento, UUID tenantId) {
        if (agendamento.getEmailCliente() == null || agendamento.getEmailCliente().isBlank()) {
            return;
        }
        DadosEmailAgendamento dados = montarDadosEmail(agendamento, tenantId, null);
        MensagemEmail mensagem = templateEmailAgendamento.criarCancelamento(dados);
        eventPublisher.publishEvent(new EventoEmailAgendamento(mensagem));
    }

    private void publicarEmailRemarcacao(Agendamento agendamento,
                                         UUID tenantId,
                                         String linkAutoatendimento,
                                         OffsetDateTime inicioAnterior,
                                         OffsetDateTime fimAnterior) {
        if (agendamento.getEmailCliente() == null || agendamento.getEmailCliente().isBlank()) {
            return;
        }
        DadosEmailAgendamento dados = montarDadosEmail(agendamento, tenantId, linkAutoatendimento);
        MensagemEmail mensagem = templateEmailAgendamento.criarRemarcacao(dados, inicioAnterior, fimAnterior);
        eventPublisher.publishEvent(new EventoEmailAgendamento(mensagem));
    }

    protected DadosEmailAgendamento montarDadosEmail(Agendamento agendamento, UUID tenantId, String link) {
        Tenant tenant = buscarTenantSeguro(tenantId);
        String nomeEstabelecimento = tenant != null && tenant.getNome() != null ? tenant.getNome() : "NoPonto";
        String fuso = tenant != null && tenant.getFusoHorario() != null
                ? tenant.getFusoHorario()
                : "America/Sao_Paulo";
        return new DadosEmailAgendamento(
                tenantId,
                agendamento.getId(),
                nomeEstabelecimento,
                fuso,
                agendamento.getNomeCliente(),
                agendamento.getEmailCliente(),
                agendamento.getProfissional().getNome(),
                agendamento.getServicoItem().getNome(),
                agendamento.getInicio(),
                agendamento.getFim(),
                link);
    }

    protected Tenant buscarTenantSeguro(UUID tenantId) {
        try {
            return tenantService.buscarPorId(tenantId);
        } catch (Exception excecao) {
            return null;
        }
    }

    @Transactional
    public AgendamentoResponse atualizarStatus(UUID agendamentoId, StatusAgendamento novoStatus, UUID tenantId) {
        Agendamento agendamento = agendamentoRepository.findByIdAndTenantId(agendamentoId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Agendamento não encontrado."));

        StatusAgendamento statusAtual = agendamento.getStatus();
        if (statusAtual == StatusAgendamento.CONCLUIDO) {
            throw new RegraNegocioExcecao("Agendamento concluído não pode ser alterado.");
        }
        if (!statusAtual.transicoesPermitidas().contains(novoStatus)) {
            throw new RegraNegocioExcecao(
                    "Transição de status inválida: " + statusAtual + " -> " + novoStatus + ".");
        }

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        if (novoStatus == StatusAgendamento.CANCELADO) {
            // A antecedência mínima vale para o cliente. O admin da clínica cancela sem essa trava.
            agendamento.setCanceladoEm(agora);
            agendamento.setCanceladoPeloCliente(Boolean.FALSE);
        }
        agendamento.setStatus(novoStatus);

        return AgendamentoResponse.de(agendamentoRepository.save(agendamento));
    }

    /**
     * Regra de antecedência mínima para cancelamento e remarcação feitos pelo cliente.
     * O admin usa {@link #atualizarStatus} e não passa por aqui.
     */
    public void validarAntecedenciaCancelamento(Agendamento agendamento, UUID tenantId, OffsetDateTime agora) {
        Tenant tenant = tenantService.buscarPorId(tenantId);
        int antecedenciaMinutos = tenant != null && tenant.getAntecedenciaMinimaCancelamentoMinutos() != null
                ? tenant.getAntecedenciaMinimaCancelamentoMinutos()
                : Tenant.ANTECEDENCIA_MINIMA_CANCELAMENTO_PADRAO_MINUTOS;
        OffsetDateTime limite = agendamento.getInicio().minusMinutes(antecedenciaMinutos);
        if (agora.isAfter(limite)) {
            String contato = montarContatoEstabelecimento(tenant, agendamento);
            throw new RegraNegocioExcecao(
                    "Cancelamento ou remarcação permitidos somente com antecedência mínima de "
                            + antecedenciaMinutos + " minutos. Entre em contato com " + contato + ".");
        }
    }

    private static String montarContatoEstabelecimento(Tenant tenant, Agendamento agendamento) {
        String nomeEstabelecimento = (tenant != null && tenant.getNome() != null && !tenant.getNome().isBlank())
                ? tenant.getNome()
                : "o estabelecimento";
        Profissional prof = agendamento != null ? agendamento.getProfissional() : null;
        if (prof != null && prof.getTelefone() != null && !prof.getTelefone().isBlank()) {
            return nomeEstabelecimento + " pelo telefone " + prof.getTelefone();
        }
        if (prof != null && prof.getEmail() != null && !prof.getEmail().isBlank()) {
            return nomeEstabelecimento + " pelo e-mail " + prof.getEmail();
        }
        return nomeEstabelecimento;
    }
}
