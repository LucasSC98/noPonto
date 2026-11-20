package me.lucascustodio.noponto.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.lucascustodio.noponto.dto.AgendamentoAutoatendimentoResponse;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.dto.AtualizarStatusRequest;
import me.lucascustodio.noponto.dto.CriarAgendamentoRequest;
import me.lucascustodio.noponto.dto.ProfissionalPublicoResponse;
import me.lucascustodio.noponto.dto.RemarcarAgendamentoRequest;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.service.AgendamentoConsultaService;
import me.lucascustodio.noponto.service.AgendamentoService;
import me.lucascustodio.noponto.service.ProfissionalService;
import me.lucascustodio.noponto.service.SlotService;
import me.lucascustodio.noponto.tenant.LimitadorTentativasAutoatendimento;
import me.lucascustodio.noponto.tenant.ResolvedorTenantPublico;
import me.lucascustodio.noponto.tenant.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class AgendamentoController {

    private final SlotService slotService;
    private final AgendamentoService agendamentoService;
    private final AgendamentoConsultaService agendamentoConsultaService;
    private final ProfissionalService profissionalService;
    private final ResolvedorTenantPublico resolvedorTenantPublico;
    private final LimitadorTentativasAutoatendimento limitadorTentativas;

    public AgendamentoController(SlotService slotService,
                                 AgendamentoService agendamentoService,
                                 AgendamentoConsultaService agendamentoConsultaService,
                                 ProfissionalService profissionalService,
                                 ResolvedorTenantPublico resolvedorTenantPublico,
                                 LimitadorTentativasAutoatendimento limitadorTentativas) {
        this.slotService = slotService;
        this.agendamentoService = agendamentoService;
        this.agendamentoConsultaService = agendamentoConsultaService;
        this.profissionalService = profissionalService;
        this.resolvedorTenantPublico = resolvedorTenantPublico;
        this.limitadorTentativas = limitadorTentativas;
    }

    @GetMapping("/public/profissionais/{id}")
    public ProfissionalPublicoResponse buscarProfissionalPublico(@PathVariable UUID id, HttpServletRequest request) {
        UUID tenantId = resolvedorTenantPublico.resolver(request);
        return profissionalService.buscarPublico(tenantId, id);
    }

    @GetMapping("/public/profissionais/{id}/servicos")
    public List<ServicoItemResponse> listarServicos(@PathVariable UUID id, HttpServletRequest request) {
        UUID tenantId = resolvedorTenantPublico.resolver(request);
        return profissionalService.listarServicosAtivos(tenantId, id);
    }

    @GetMapping("/public/profissionais/{id}/slots")
    public List<OffsetDateTime> listarSlots(@PathVariable UUID id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                            @RequestParam UUID servicoId,
                                            HttpServletRequest request) {
        UUID tenantId = resolvedorTenantPublico.resolver(request);
        return slotService.calcularSlotsDisponiveis(id, data, tenantId, servicoId);
    }

    @PostMapping("/public/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResponse criar(@Valid @RequestBody CriarAgendamentoRequest requisicao,
                                     HttpServletRequest request) {
        UUID tenantId = resolvedorTenantPublico.resolver(request, requisicao.tenant());
        return agendamentoService.criar(requisicao, tenantId);
    }

    @GetMapping("/public/appointments/manage/{token}")
    public AgendamentoAutoatendimentoResponse consultarPorToken(@PathVariable String token,
                                                                HttpServletRequest request) {
        limitadorTentativas.verificar(request);
        return agendamentoService.consultarPorToken(token);
    }

    @GetMapping("/public/appointments/manage/{token}/slots")
    public List<OffsetDateTime> listarSlotsAutoatendimento(
            @PathVariable String token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        limitadorTentativas.verificar(request);
        return agendamentoService.listarSlotsParaRemarcacao(token, data);
    }

    @PostMapping("/public/appointments/manage/{token}/cancel")
    public AgendamentoAutoatendimentoResponse cancelarPorToken(@PathVariable String token,
                                                               HttpServletRequest request) {
        limitadorTentativas.verificar(request);
        return agendamentoService.cancelarPorToken(token);
    }

    @PostMapping("/public/appointments/manage/{token}/reschedule")
    public AgendamentoAutoatendimentoResponse remarcarPorToken(
            @PathVariable String token,
            @RequestBody(required = false) RemarcarAgendamentoRequest requisicao,
            HttpServletRequest request) {
        limitadorTentativas.verificar(request);
        return agendamentoService.remarcarPorToken(token, requisicao);
    }

    @GetMapping("/agendamentos")
    public List<AgendamentoResponse> listar() {
        return agendamentoConsultaService.listar(TenantContextHolder.get());
    }

    @PatchMapping("/agendamentos/{id}/status")
    public AgendamentoResponse atualizarStatus(@PathVariable UUID id,
                                               @Valid @RequestBody AtualizarStatusRequest requisicao) {
        return agendamentoService.atualizarStatus(id, requisicao.status(), TenantContextHolder.get());
    }
}
