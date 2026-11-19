package me.lucascustodio.noponto.controller;

import jakarta.validation.Valid;
import me.lucascustodio.noponto.dto.AtualizarProfissionalRequest;
import me.lucascustodio.noponto.dto.AtualizarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.AtualizarServicoItemRequest;
import me.lucascustodio.noponto.dto.CriarProfissionalRequest;
import me.lucascustodio.noponto.dto.CriarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.CriarServicoItemRequest;
import me.lucascustodio.noponto.dto.ProfissionalResponse;
import me.lucascustodio.noponto.dto.RegraDisponibilidadeResponse;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.service.GestaoProfissionalService;
import me.lucascustodio.noponto.service.GestaoRegraDisponibilidadeService;
import me.lucascustodio.noponto.service.GestaoServicoItemService;
import me.lucascustodio.noponto.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<ProfissionalResponse> listarProfissionais() {
        return gestaoProfissionalService.listar(TenantContextHolder.get());
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

    // --- Serviços ---

    @GetMapping("/servicos")
    public List<ServicoItemResponse> listarServicos(@RequestParam UUID profissionalId) {
        return gestaoServicoItemService.listarPorProfissional(TenantContextHolder.get(), profissionalId);
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

    // --- Regras de disponibilidade ---

    @GetMapping("/regras-disponibilidade")
    public List<RegraDisponibilidadeResponse> listarRegras(@RequestParam UUID profissionalId) {
        return gestaoRegraDisponibilidadeService.listarPorProfissional(TenantContextHolder.get(), profissionalId);
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
}
