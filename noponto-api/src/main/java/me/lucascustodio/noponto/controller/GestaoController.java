package me.lucascustodio.noponto.controller;

import jakarta.validation.Valid;
import me.lucascustodio.noponto.dto.AtualizarAtivoRequest;
import me.lucascustodio.noponto.dto.AtualizarProfissionalRequest;
import me.lucascustodio.noponto.dto.AtualizarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.AtualizarServicoItemRequest;
import me.lucascustodio.noponto.dto.CriarProfissionalRequest;
import me.lucascustodio.noponto.dto.CriarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.CriarServicoItemRequest;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.dto.RegraDisponibilidadeResponse;
import me.lucascustodio.noponto.dto.SalvarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.service.GestaoProfissionalService;
import me.lucascustodio.noponto.service.GestaoRegraDisponibilidadeService;
import me.lucascustodio.noponto.service.GestaoServicoItemService;
import me.lucascustodio.noponto.tenant.TenantContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class GestaoController {

    private final GestaoProfissionalService gestaoProfissionalService;
    private final GestaoServicoItemService gestaoServicoItemService;
    private final GestaoRegraDisponibilidadeService gestaoRegraDisponibilidadeService;

    public GestaoController(GestaoProfissionalService gestaoProfissionalService,
                            GestaoServicoItemService gestaoServicoItemService,
                            GestaoRegraDisponibilidadeService gestaoRegraDisponibilidadeService) {
        this.gestaoProfissionalService = gestaoProfissionalService;
        this.gestaoServicoItemService = gestaoServicoItemService;
        this.gestaoRegraDisponibilidadeService = gestaoRegraDisponibilidadeService;
    }

    // --- Profissionais ---

    @GetMapping("/profissionais")
    public Page<ProfissionalResponse> listarProfissionais(@PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return gestaoProfissionalService.listar(TenantContextHolder.get(), pageable);
    }

    @PostMapping("/profissionais")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalResponse criarProfissional(@Valid @RequestBody CriarProfissionalRequest requisicao) {
        return gestaoProfissionalService.criar(requisicao, TenantContextHolder.get());
    }

    @PutMapping("/profissionais/{id}")
    public ProfissionalResponse atualizarProfissional(@PathVariable UUID id,
                                                       @Valid @RequestBody AtualizarProfissionalRequest requisicao) {
        return gestaoProfissionalService.atualizar(id, requisicao, TenantContextHolder.get());
    }

    @PatchMapping("/professionals/{id}/status")
    public ProfissionalResponse atualizarStatusProfissional(@PathVariable UUID id,
                                                            @Valid @RequestBody AtualizarAtivoRequest requisicao) {
        return gestaoProfissionalService.atualizarAtivo(id, requisicao.ativo(), TenantContextHolder.get());
    }

    // --- Serviços ---

    @GetMapping("/servicos")
    public Page<ServicoItemResponse> listarServicos(@RequestParam UUID profissionalId,
                                                    @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return gestaoServicoItemService.listarPorProfissional(TenantContextHolder.get(), profissionalId, pageable);
    }

    @PostMapping("/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicoItemResponse criarServico(@Valid @RequestBody CriarServicoItemRequest requisicao) {
        return gestaoServicoItemService.criar(requisicao, TenantContextHolder.get());
    }

    @PutMapping("/servicos/{id}")
    public ServicoItemResponse atualizarServico(@PathVariable UUID id,
                                                 @Valid @RequestBody AtualizarServicoItemRequest requisicao) {
        return gestaoServicoItemService.atualizar(id, requisicao, TenantContextHolder.get());
    }

    @PatchMapping("/services/{id}/status")
    public ServicoItemResponse atualizarStatusServico(@PathVariable UUID id,
                                                      @Valid @RequestBody AtualizarAtivoRequest requisicao) {
        return gestaoServicoItemService.atualizarAtivo(id, requisicao.ativo(), TenantContextHolder.get());
    }

    // --- Regras de disponibilidade ---

    @GetMapping("/regras-disponibilidade")
    public Page<RegraDisponibilidadeResponse> listarRegras(@RequestParam UUID profissionalId,
                                                           @PageableDefault(size = 20, sort = "diaSemana") Pageable pageable) {
        return gestaoRegraDisponibilidadeService.listarPorProfissional(TenantContextHolder.get(), profissionalId, pageable);
    }

    @PostMapping("/regras-disponibilidade")
    @ResponseStatus(HttpStatus.CREATED)
    public RegraDisponibilidadeResponse criarRegra(@Valid @RequestBody CriarRegraDisponibilidadeRequest requisicao) {
        return gestaoRegraDisponibilidadeService.criar(requisicao, TenantContextHolder.get());
    }

    @PutMapping("/regras-disponibilidade/{id}")
    public RegraDisponibilidadeResponse atualizarRegra(@PathVariable UUID id,
                                                        @Valid @RequestBody AtualizarRegraDisponibilidadeRequest requisicao) {
        return gestaoRegraDisponibilidadeService.atualizar(id, requisicao, TenantContextHolder.get());
    }

    @GetMapping("/professionals/{id}/availability-rules")
    public List<RegraDisponibilidadeResponse> listarRegrasDoProfissional(@PathVariable UUID id) {
        return gestaoRegraDisponibilidadeService.listarDoProfissional(TenantContextHolder.get(), id);
    }

    @PostMapping("/professionals/{id}/availability-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RegraDisponibilidadeResponse criarRegraDoProfissional(@PathVariable UUID id,
                                                                 @Valid @RequestBody SalvarRegraDisponibilidadeRequest requisicao) {
        return gestaoRegraDisponibilidadeService.criarParaProfissional(id, requisicao, TenantContextHolder.get());
    }

    @PutMapping("/professionals/{id}/availability-rules/{regraId}")
    public RegraDisponibilidadeResponse atualizarRegraDoProfissional(@PathVariable UUID id,
                                                                     @PathVariable UUID regraId,
                                                                     @Valid @RequestBody SalvarRegraDisponibilidadeRequest requisicao) {
        return gestaoRegraDisponibilidadeService.atualizarParaProfissional(
                id, regraId, requisicao, TenantContextHolder.get());
    }

    @DeleteMapping("/professionals/{id}/availability-rules/{regraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRegraDoProfissional(@PathVariable UUID id, @PathVariable UUID regraId) {
        gestaoRegraDisponibilidadeService.excluir(id, regraId, TenantContextHolder.get());
    }
}
