package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Motor de slots. Calcula em memória, nunca persiste horários vazios.
 * Cruza: RegraDisponibilidade x Bloqueio x Agendamento (PENDENTE/CONFIRMADO).
 * O slot tem a duração do ServicoItem; intervalos são semiabertos {@code [inicio, fim)}.
 */
@Service
public class SlotService {

    private final ProfissionalService profissionalService;
    private final AgendamentoConsultaService agendamentoConsultaService;
    private final RegraDisponibilidadeRepository regraDisponibilidadeRepository;
    private final BloqueioRepository bloqueioRepository;
    private final Clock relogio;

    public SlotService(ProfissionalService profissionalService,
                       AgendamentoConsultaService agendamentoConsultaService,
                       RegraDisponibilidadeRepository regraDisponibilidadeRepository,
                       BloqueioRepository bloqueioRepository,
                       Clock relogio) {
        this.profissionalService = profissionalService;
        this.agendamentoConsultaService = agendamentoConsultaService;
        this.regraDisponibilidadeRepository = regraDisponibilidadeRepository;
        this.bloqueioRepository = bloqueioRepository;
        this.relogio = relogio;
    }

    /**
     * @param data data local do atendimento, interpretada no offset de cada regra de disponibilidade
     * @return inícios dos slots livres, em UTC e em ordem crescente
     */
    @Transactional(readOnly = true)
    public List<OffsetDateTime> calcularSlotsDisponiveis(UUID profissionalId, LocalDate data,
                                                         UUID tenantId, UUID servicoItemId) {
        profissionalService.buscarAtivo(tenantId, profissionalId);
        ServicoItem servico = profissionalService.buscarServicoAtivo(tenantId, profissionalId, servicoItemId);
        Duration duracao = Duration.ofMinutes(servico.getDuracaoMinutos());

        List<RegraDisponibilidade> regras = regraDisponibilidadeRepository
                .findAllByTenantIdAndProfissionalIdAndDiaSemanaAndAtivoTrue(tenantId, profissionalId, data.getDayOfWeek());
        if (regras.isEmpty()) {
            return List.of();
        }

        List<Janela> janelas = regras.stream().map(regra -> Janela.de(regra, data)).toList();
        OffsetDateTime inicioBusca = janelas.stream().map(Janela::inicio)
                .min(Comparator.comparing(OffsetDateTime::toInstant)).orElseThrow();
        OffsetDateTime fimBusca = janelas.stream().map(Janela::fim)
                .max(Comparator.comparing(OffsetDateTime::toInstant)).orElseThrow();

        List<Bloqueio> bloqueios = bloqueioRepository
                .buscarInterceptando(tenantId, profissionalId, inicioBusca, fimBusca);
        List<Agendamento> agendamentos = agendamentoConsultaService
                .buscarOcupados(tenantId, profissionalId, inicioBusca, fimBusca);

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        TreeSet<OffsetDateTime> slots = new TreeSet<>();
        for (Janela janela : janelas) {
            OffsetDateTime inicioSlot = janela.inicio();
            OffsetDateTime fimSlot = inicioSlot.plus(duracao);
            while (!fimSlot.isAfter(janela.fim())) {
                if (inicioSlot.isAfter(agora) && estaLivre(inicioSlot, fimSlot, bloqueios, agendamentos)) {
                    slots.add(inicioSlot.withOffsetSameInstant(ZoneOffset.UTC));
                }
                inicioSlot = fimSlot;
                fimSlot = inicioSlot.plus(duracao);
            }
        }
        return List.copyOf(slots);
    }

    /**
     * Indica se {@code inicio} é exatamente o começo de um slot livre. A data local do slot depende do
     * offset da regra, então testa o dia UTC do instante e os dois vizinhos.
     */
    @Transactional(readOnly = true)
    public boolean slotDisponivel(UUID profissionalId, OffsetDateTime inicio, UUID tenantId, UUID servicoItemId) {
        LocalDate dataUtc = inicio.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        for (int deslocamentoDias = -1; deslocamentoDias <= 1; deslocamentoDias++) {
            List<OffsetDateTime> slots = calcularSlotsDisponiveis(
                    profissionalId, dataUtc.plusDays(deslocamentoDias), tenantId, servicoItemId);
            if (slots.stream().anyMatch(slot -> slot.toInstant().equals(inicio.toInstant()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean estaLivre(OffsetDateTime inicio, OffsetDateTime fim,
                                     List<Bloqueio> bloqueios, List<Agendamento> agendamentos) {
        boolean bloqueado = bloqueios.stream()
                .anyMatch(bloqueio -> sobrepoe(inicio, fim, bloqueio.getInicio(), bloqueio.getFim()));
        boolean ocupado = agendamentos.stream()
                .anyMatch(agendamento -> sobrepoe(inicio, fim, agendamento.getInicio(), agendamento.getFim()));
        return !bloqueado && !ocupado;
    }

    /** Qualquer sobreposição, inclusive parcial, descarta o slot. Intervalos semiabertos. */
    private static boolean sobrepoe(OffsetDateTime inicioA, OffsetDateTime fimA,
                                    OffsetDateTime inicioB, OffsetDateTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }

    private record Janela(OffsetDateTime inicio, OffsetDateTime fim) {

        static Janela de(RegraDisponibilidade regra, LocalDate data) {
            return new Janela(
                    OffsetDateTime.of(data, regra.getHoraInicio().toLocalTime(), regra.getHoraInicio().getOffset()),
                    OffsetDateTime.of(data, regra.getHoraFim().toLocalTime(), regra.getHoraFim().getOffset()));
        }
    }
}
