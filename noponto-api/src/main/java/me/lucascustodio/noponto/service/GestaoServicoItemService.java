package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.dto.AtualizarServicoItemRequest;
import me.lucascustodio.noponto.dto.CriarServicoItemRequest;
import me.lucascustodio.noponto.dto.ServicoItemResponse;
import me.lucascustodio.noponto.dto.ServicoVinculadoResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.repository.ContagemPorProfissional;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.ServicoVinculadoPorProfissional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GestaoServicoItemService {

    private final ServicoItemRepository servicoItemRepository;
    private final ProfissionalRepository profissionalRepository;

    public GestaoServicoItemService(ServicoItemRepository servicoItemRepository,
                                    ProfissionalRepository profissionalRepository) {
        this.servicoItemRepository = servicoItemRepository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional(readOnly = true)
    public Page<ServicoItemResponse> listarPorProfissional(UUID tenantId, UUID profissionalId, Pageable pageable) {
        return servicoItemRepository.findAllByTenantIdAndProfissionalId(tenantId, profissionalId, pageable)
                .map(ServicoItemResponse::de);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> contarAtivosPorProfissional(UUID tenantId, Collection<UUID> profissionalIds) {
        if (profissionalIds == null || profissionalIds.isEmpty()) {
            return Map.of();
        }
        return servicoItemRepository.contarAtivosPorProfissional(tenantId, profissionalIds).stream()
                .collect(Collectors.toMap(ContagemPorProfissional::profissionalId, ContagemPorProfissional::total));
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ServicoVinculadoResponse>> listarAtivosPorProfissionais(
            UUID tenantId, Collection<UUID> profissionalIds) {
        if (profissionalIds == null || profissionalIds.isEmpty()) {
            return Map.of();
        }
        return servicoItemRepository.buscarAtivosPorProfissionais(tenantId, profissionalIds).stream()
                .collect(Collectors.groupingBy(
                        ServicoVinculadoPorProfissional::profissionalId,
                        Collectors.mapping(
                                servico -> new ServicoVinculadoResponse(
                                        servico.id(), servico.nome(), servico.duracaoMinutos()),
                                Collectors.toList())));
    }

    @Transactional
    public ServicoItemResponse criar(CriarServicoItemRequest requisicao, UUID tenantId) {
        Profissional profissional = profissionalRepository.findByIdAndTenantId(requisicao.profissionalId(), tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        ServicoItem servico = new ServicoItem();
        servico.setTenantId(tenantId);
        servico.setProfissional(profissional);
        servico.setNome(requisicao.nome());
        servico.setDuracaoMinutos(requisicao.duracaoMinutos());
        servico.setAtivo(true);

        return ServicoItemResponse.de(servicoItemRepository.save(servico));
    }

    @Transactional
    public ServicoItemResponse atualizar(UUID id, AtualizarServicoItemRequest requisicao, UUID tenantId) {
        ServicoItem servico = servicoItemRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Serviço não encontrado."));

        servico.setNome(requisicao.nome());
        servico.setDuracaoMinutos(requisicao.duracaoMinutos());
        servico.setAtivo(requisicao.ativo());

        return ServicoItemResponse.de(servicoItemRepository.save(servico));
    }

    @Transactional
    public ServicoItemResponse atualizarAtivo(UUID id, boolean ativo, UUID tenantId) {
        ServicoItem servico = servicoItemRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Serviço não encontrado."));
        servico.setAtivo(ativo);
        return ServicoItemResponse.de(servicoItemRepository.save(servico));
    }
}
