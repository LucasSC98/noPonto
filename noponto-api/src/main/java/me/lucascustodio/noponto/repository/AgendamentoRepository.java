package me.lucascustodio.noponto.repository;

import me.lucascustodio.noponto.domain.Agendamento;
import me.lucascustodio.noponto.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends Repository<Agendamento, UUID> {

    Agendamento save(Agendamento agendamento);

    Optional<Agendamento> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select a from Agendamento a
            join fetch a.profissional
            join fetch a.servicoItem
            where a.tokenGerenciamentoHash = :tokenHash
            """)
    Optional<Agendamento> buscarPorTokenGerenciamentoHash(@Param("tokenHash") String tokenHash);

    List<Agendamento> findAllByTenantIdOrderByInicioAsc(UUID tenantId);

    /** Agendamentos com os status informados que interceptam o intervalo {@code [inicio, fim)}. */
    @Query("""
            select a from Agendamento a
            where a.tenantId = :tenantId
              and a.profissional.id = :profissionalId
              and a.status in :status
              and a.inicio < :fim
              and a.fim > :inicio
            order by a.inicio
            """)
    List<Agendamento> buscarInterceptando(@Param("tenantId") UUID tenantId,
                                          @Param("profissionalId") UUID profissionalId,
                                          @Param("status") List<StatusAgendamento> status,
                                          @Param("inicio") OffsetDateTime inicio,
                                          @Param("fim") OffsetDateTime fim);

    /** Agendamentos com serviço carregado que interceptam o intervalo {@code [inicio, fim)}. */
    @Query("""
            select a from Agendamento a
            join fetch a.servicoItem
            where a.tenantId = :tenantId
              and a.profissional.id = :profissionalId
              and a.status in :status
              and a.inicio < :fim
              and a.fim > :inicio
            order by a.inicio
            """)
    List<Agendamento> buscarInterceptandoComServico(@Param("tenantId") UUID tenantId,
                                                    @Param("profissionalId") UUID profissionalId,
                                                    @Param("status") List<StatusAgendamento> status,
                                                    @Param("inicio") OffsetDateTime inicio,
                                                    @Param("fim") OffsetDateTime fim);

    /** Conta agendamentos nos status informados com início dentro de {@code [inicioPeriodo, fimPeriodo)}. */
    @Query("""
            select new me.lucascustodio.noponto.repository.ContagemPorProfissional(a.profissional.id, count(a))
            from Agendamento a
            where a.tenantId = :tenantId
              and a.profissional.id in :profissionalIds
              and a.status in :status
              and a.inicio >= :inicioPeriodo
              and a.inicio < :fimPeriodo
            group by a.profissional.id
            """)
    List<ContagemPorProfissional> contarPorProfissionalNoPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("profissionalIds") Collection<UUID> profissionalIds,
            @Param("status") List<StatusAgendamento> status,
            @Param("inicioPeriodo") OffsetDateTime inicioPeriodo,
            @Param("fimPeriodo") OffsetDateTime fimPeriodo);

    /**
     * Busca agendamentos ativos na janela de lembrete (cerca de 24h antes) que ainda não receberam lembrete.
     */
    @Query("""
            select a from Agendamento a
            join fetch a.profissional
            join fetch a.servicoItem
            where a.status in :status
              and a.lembreteEnviadoEm is null
              and a.emailCliente is not null
              and a.inicio >= :janelaInicio
              and a.inicio <= :janelaFim
            order by a.inicio
            """)
    List<Agendamento> buscarPendentesDeLembrete(
            @Param("status") List<StatusAgendamento> status,
            @Param("janelaInicio") OffsetDateTime janelaInicio,
            @Param("janelaFim") OffsetDateTime janelaFim);

    /**
     * Reserva atomicamente o envio do lembrete para garantir idempotência mesmo com múltiplas
     * execuções ou múltiplas instâncias da aplicação. Retorna 1 se reservou, 0 se já havia sido enviado
     * ou se o status deixou de ser ativo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Agendamento a
            set a.lembreteEnviadoEm = :enviadoEm,
                a.atualizadoEm = :enviadoEm
            where a.id = :id
              and a.tenantId = :tenantId
              and a.status in :status
              and a.lembreteEnviadoEm is null
            """)
    int reservarEnvioLembrete(@Param("id") UUID id,
                              @Param("tenantId") UUID tenantId,
                              @Param("status") List<StatusAgendamento> status,
                              @Param("enviadoEm") OffsetDateTime enviadoEm);
}
