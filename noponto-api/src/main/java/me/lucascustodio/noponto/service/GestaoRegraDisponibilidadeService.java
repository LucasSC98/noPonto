package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.dto.AtualizarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.CriarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.RegraDisponibilidadeResponse;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GestaoRegraDisponibilidadeService {

    private final RegraDisponibilidadeRepository regraDisponibilidadeRepository;
    private final ProfissionalRepository profissionalRepository;

    public GestaoRegraDisponibilidadeService(RegraDisponibilidadeRepository regraDisponibilidadeRepository,
                                             ProfissionalRepository profissionalRepository) {
        this.regraDisponibilidadeRepository = regraDisponibilidadeRepository;
        this.profissionalRepository = profissionalRepository;
    }

    @Transactional(readOnly = true)
    public List<RegraDisponibilidadeResponse> listarPorProfissional(UUID tenantId, UUID profissionalId) {
        return regraDisponibilidadeRepository.findAllByTenantIdAndProfissionalId(tenantId, profissionalId)
                .stream()
                .map(RegraDisponibilidadeResponse::de)
                .toList();
    }

    @Transactional
    public RegraDisponibilidadeResponse criar(CriarRegraDisponibilidadeRequest requisicao, UUID tenantId) {
        if (!requisicao.horaInicio().isBefore(requisicao.horaFim())) {
            throw new RegraNegocioExcecao("Horário de início deve ser anterior ao horário de fim.");
        }

        Profissional profissional = profissionalRepository.findByIdAndTenantId(requisicao.profissionalId(), tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));

        RegraDisponibilidade regra = new RegraDisponibilidade();
        regra.setTenantId(tenantId);
        regra.setProfissional(profissional);
        regra.setDiaSemana(requisicao.diaSemana());
        regra.setHoraInicio(requisicao.horaInicio());
        regra.setHoraFim(requisicao.horaFim());
        regra.setAtivo(true);

        return RegraDisponibilidadeResponse.de(regraDisponibilidadeRepository.save(regra));
    }

    @Transactional
    public RegraDisponibilidadeResponse atualizar(UUID id, AtualizarRegraDisponibilidadeRequest requisicao, UUID tenantId) {
        if (!requisicao.horaInicio().isBefore(requisicao.horaFim())) {
            throw new RegraNegocioExcecao("Horário de início deve ser anterior ao horário de fim.");
        }

        RegraDisponibilidade regra = regraDisponibilidadeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Regra de disponibilidade não encontrada."));

        regra.setDiaSemana(requisicao.diaSemana());
        regra.setHoraInicio(requisicao.horaInicio());
        regra.setHoraFim(requisicao.horaFim());
        regra.setAtivo(requisicao.ativo());

        return RegraDisponibilidadeResponse.de(regraDisponibilidadeRepository.save(regra));
    }
}
