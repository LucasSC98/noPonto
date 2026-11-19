package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.dto.AtualizarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.CriarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.dto.RegraDisponibilidadeResponse;
import me.lucascustodio.noponto.dto.SalvarRegraDisponibilidadeRequest;
import me.lucascustodio.noponto.exception.RecursoNaoEncontradoExcecao;
import me.lucascustodio.noponto.exception.RegraNegocioExcecao;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.Comparator;
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
    public Page<RegraDisponibilidadeResponse> listarPorProfissional(UUID tenantId, UUID profissionalId, Pageable pageable) {
        return regraDisponibilidadeRepository.findAllByTenantIdAndProfissionalId(tenantId, profissionalId, pageable)
                .map(RegraDisponibilidadeResponse::de);
    }

    @Transactional(readOnly = true)
    public List<RegraDisponibilidadeResponse> listarDoProfissional(UUID tenantId, UUID profissionalId) {
        exigirProfissionalDoTenant(profissionalId, tenantId);
        return regraDisponibilidadeRepository.findAllByTenantIdAndProfissionalId(tenantId, profissionalId)
                .stream()
                .sorted(Comparator.comparing(RegraDisponibilidade::getDiaSemana)
                        .thenComparing(RegraDisponibilidade::getHoraInicio))
                .map(RegraDisponibilidadeResponse::de)
                .toList();
    }

    @Transactional
    public RegraDisponibilidadeResponse criar(CriarRegraDisponibilidadeRequest requisicao, UUID tenantId) {
        return criarParaProfissional(requisicao.profissionalId(),
                new SalvarRegraDisponibilidadeRequest(
                        requisicao.diaSemana(), requisicao.horaInicio(), requisicao.horaFim()),
                tenantId);
    }

    @Transactional
    public RegraDisponibilidadeResponse criarParaProfissional(UUID profissionalId,
                                                              SalvarRegraDisponibilidadeRequest requisicao,
                                                              UUID tenantId) {
        validarIntervalo(requisicao.horaInicio(), requisicao.horaFim());
        Profissional profissional = exigirProfissionalDoTenant(profissionalId, tenantId);
        validarSobreposicao(tenantId, profissionalId, requisicao.diaSemana(),
                requisicao.horaInicio(), requisicao.horaFim(), null);

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
        validarIntervalo(requisicao.horaInicio(), requisicao.horaFim());

        RegraDisponibilidade regra = regraDisponibilidadeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Regra de disponibilidade não encontrada."));

        validarSobreposicao(tenantId, regra.getProfissional().getId(), requisicao.diaSemana(),
                requisicao.horaInicio(), requisicao.horaFim(), regra.getId());

        regra.setDiaSemana(requisicao.diaSemana());
        regra.setHoraInicio(requisicao.horaInicio());
        regra.setHoraFim(requisicao.horaFim());
        regra.setAtivo(requisicao.ativo());

        return RegraDisponibilidadeResponse.de(regraDisponibilidadeRepository.save(regra));
    }

    @Transactional
    public RegraDisponibilidadeResponse atualizarParaProfissional(UUID profissionalId,
                                                                  UUID regraId,
                                                                  SalvarRegraDisponibilidadeRequest requisicao,
                                                                  UUID tenantId) {
        validarIntervalo(requisicao.horaInicio(), requisicao.horaFim());
        exigirProfissionalDoTenant(profissionalId, tenantId);

        RegraDisponibilidade regra = regraDisponibilidadeRepository
                .findByIdAndTenantIdAndProfissionalId(regraId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Regra de disponibilidade não encontrada."));

        validarSobreposicao(tenantId, profissionalId, requisicao.diaSemana(),
                requisicao.horaInicio(), requisicao.horaFim(), regra.getId());

        regra.setDiaSemana(requisicao.diaSemana());
        regra.setHoraInicio(requisicao.horaInicio());
        regra.setHoraFim(requisicao.horaFim());

        return RegraDisponibilidadeResponse.de(regraDisponibilidadeRepository.save(regra));
    }

    @Transactional
    public void excluir(UUID profissionalId, UUID regraId, UUID tenantId) {
        exigirProfissionalDoTenant(profissionalId, tenantId);
        RegraDisponibilidade regra = regraDisponibilidadeRepository
                .findByIdAndTenantIdAndProfissionalId(regraId, tenantId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Regra de disponibilidade não encontrada."));
        regraDisponibilidadeRepository.delete(regra);
    }

    private Profissional exigirProfissionalDoTenant(UUID profissionalId, UUID tenantId) {
        return profissionalRepository.findByIdAndTenantId(profissionalId, tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoExcecao("Profissional não encontrado."));
    }

    private static void validarIntervalo(OffsetTime horaInicio, OffsetTime horaFim) {
        if (!horaFim.isAfter(horaInicio)) {
            throw new RegraNegocioExcecao("A hora de fim deve ser maior que a hora de início.");
        }
    }

    private void validarSobreposicao(UUID tenantId,
                                     UUID profissionalId,
                                     DayOfWeek diaSemana,
                                     OffsetTime horaInicio,
                                     OffsetTime horaFim,
                                     UUID regraIgnoradaId) {
        boolean sobrepoe = regraDisponibilidadeRepository
                .findAllByTenantIdAndProfissionalIdAndDiaSemana(tenantId, profissionalId, diaSemana)
                .stream()
                .filter(regra -> regraIgnoradaId == null || !regraIgnoradaId.equals(regra.getId()))
                .anyMatch(regra -> intervalosSobrepoem(horaInicio, horaFim, regra.getHoraInicio(), regra.getHoraFim()));

        if (sobrepoe) {
            throw new RegraNegocioExcecao(
                    "Não pode haver sobreposição de horários no mesmo dia da semana para o profissional.");
        }
    }

    /** Intervalos semiabertos: faixas adjacentes (fim de uma igual ao início da outra) não se sobrepõem. */
    private static boolean intervalosSobrepoem(OffsetTime inicioA, OffsetTime fimA,
                                               OffsetTime inicioB, OffsetTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }
}
