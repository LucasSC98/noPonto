package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import me.lucascustodio.noponto.repository.ContagemPorProfissional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leituras do domínio Agendamento. Separado do {@link AgendamentoService} (escritas) para que o
 * {@link SlotService} consulte os horários ocupados sem dependência circular.
 */
@Service
public class AgendamentoConsultaService {

    private final AgendamentoRepository agendamentoRepository;
    private final Clock relogio;

    public AgendamentoConsultaService(AgendamentoRepository agendamentoRepository, Clock relogio) {
        this.agendamentoRepository = agendamentoRepository;
        this.relogio = relogio;
    }

    /** Agendamentos PENDENTE ou CONFIRMADO que interceptam {@code [inicio, fim)}. */
    @Transactional(readOnly = true)
    public List<Agendamento> buscarOcupados(UUID tenantId, UUID profissionalId,
                                            OffsetDateTime inicio, OffsetDateTime fim) {
        return agendamentoRepository.buscarInterceptando(
                tenantId, profissionalId, StatusAgendamento.ocupamHorario(), inicio, fim);
    }

    /** Agendamentos PENDENTE ou CONFIRMADO (com serviço carregado) que interceptam {@code [inicio, fim)}. */
    @Transactional(readOnly = true)
    public List<Agendamento> buscarOcupadosComServico(UUID tenantId, UUID profissionalId,
                                                      OffsetDateTime inicio, OffsetDateTime fim) {
        return agendamentoRepository.buscarInterceptandoComServico(
                tenantId, profissionalId, StatusAgendamento.ocupamHorario(), inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar(UUID tenantId) {
        return agendamentoRepository.findAllByTenantIdOrderByInicioAsc(tenantId).stream()
                .map(AgendamentoResponse::de)
                .toList();
    }

    /**
     * Conta agendamentos PENDENTE ou CONFIRMADO por profissional na semana atual (segunda a domingo em UTC).
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> contarAgendamentosSemanaPorProfissional(UUID tenantId, Collection<UUID> profissionalIds) {
        if (profissionalIds == null || profissionalIds.isEmpty()) {
            return Map.of();
        }
        LocalDate segunda = LocalDate.now(relogio.withZone(ZoneOffset.UTC))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime inicioSemana = segunda.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fimSemana = inicioSemana.plusDays(7);

        return agendamentoRepository.contarPorProfissionalNoPeriodo(
                        tenantId, profissionalIds, StatusAgendamento.ocupamHorario(), inicioSemana, fimSemana)
                .stream()
                .collect(Collectors.toMap(ContagemPorProfissional::profissionalId, ContagemPorProfissional::total));
    }
}
