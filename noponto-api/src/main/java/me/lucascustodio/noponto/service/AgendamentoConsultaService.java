package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import me.lucascustodio.noponto.dto.AgendamentoResponse;
import me.lucascustodio.noponto.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Leituras do domínio Agendamento. Separado do {@link AgendamentoService} (escritas) para que o
 * {@link SlotService} consulte os horários ocupados sem dependência circular.
 */
@Service
public class AgendamentoConsultaService {

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoConsultaService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    /** Agendamentos PENDENTE ou CONFIRMADO que interceptam {@code [inicio, fim)}. */
    @Transactional(readOnly = true)
    public List<Agendamento> buscarOcupados(UUID tenantId, UUID profissionalId,
                                            OffsetDateTime inicio, OffsetDateTime fim) {
        return agendamentoRepository.buscarInterceptando(
                tenantId, profissionalId, StatusAgendamento.ocupamHorario(), inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar(UUID tenantId) {
        return agendamentoRepository.findAllByTenantIdOrderByInicioAsc(tenantId).stream()
                .map(AgendamentoResponse::de)
                .toList();
    }
}
