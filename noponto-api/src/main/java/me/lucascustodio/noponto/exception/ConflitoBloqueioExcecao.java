package me.lucascustodio.noponto.exception;

import me.lucascustodio.noponto.dto.AgendamentoConflitanteResponse;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Lançada quando a criação ou edição de um bloqueio intercepta agendamentos PENDENTE ou CONFIRMADO
 * sem confirmação explícita ({@code confirmarMesmoComConflito = true}).
 */
public class ConflitoBloqueioExcecao extends ExcecaoDominio {

    private final List<AgendamentoConflitanteResponse> agendamentosConflitantes;

    public ConflitoBloqueioExcecao(List<AgendamentoConflitanteResponse> agendamentosConflitantes) {
        super(agendamentosConflitantes.size() == 1
                        ? "Existe 1 agendamento ativo neste período. Confirme para criar o bloqueio mesmo assim."
                        : "Existem agendamentos ativos neste período. Confirme para criar o bloqueio mesmo assim.",
                HttpStatus.CONFLICT);
        this.agendamentosConflitantes = List.copyOf(agendamentosConflitantes);
    }

    public List<AgendamentoConflitanteResponse> getAgendamentosConflitantes() {
        return agendamentosConflitantes;
    }
}
