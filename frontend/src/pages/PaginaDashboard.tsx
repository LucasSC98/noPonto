import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { BadgeStatus } from '../components/BadgeStatus';
import { BarraPainel } from '../components/BarraPainel';
import {
  atualizarStatusAgendamento,
  listarAgendamentos,
} from '../services/agendamentoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import { listarBloqueios } from '../services/gestaoService';
import { ROTULO_STATUS, TRANSICOES_STATUS } from '../types/dominio';
import type { AgendamentoResponse, StatusAgendamento } from '../types/dominio';
import type { BloqueioResponse } from '../types/gestao';
import {
  encontrarBloqueiosConflitantesComAgendamento,
  formatarPeriodoBloqueio,
} from '../utils/bloqueios';
import { formatarDataHoraLocal } from '../utils/datas';
import estilos from './PaginaDashboard.module.css';

type FiltroStatus = 'TODOS' | StatusAgendamento;

const FILTROS: { valor: FiltroStatus; rotulo: string }[] = [
  { valor: 'TODOS', rotulo: 'Todos' },
  { valor: 'PENDENTE', rotulo: 'Pendentes' },
  { valor: 'CONFIRMADO', rotulo: 'Confirmados' },
  { valor: 'CONCLUIDO', rotulo: 'Concluídos' },
  { valor: 'CANCELADO', rotulo: 'Cancelados' },
];

const ROTULO_ACAO: Record<StatusAgendamento, string> = {
  PENDENTE: 'Pendente',
  CONFIRMADO: 'Confirmar',
  CONCLUIDO: 'Concluir',
  CANCELADO: 'Cancelar',
};

function abreviarUuid(identificador: string): string {
  return identificador.length > 12 ? `${identificador.slice(0, 8)}...` : identificador;
}

async function carregarBloqueiosDosAgendamentos(
  lista: AgendamentoResponse[],
): Promise<Record<string, BloqueioResponse[]>> {
  const ativos = lista.filter((a) => a.status === 'PENDENTE' || a.status === 'CONFIRMADO');
  if (ativos.length === 0) {
    return {};
  }

  let menorInicio = Date.parse(ativos[0].inicio);
  let maiorFim = Date.parse(ativos[0].fim);
  for (const agendamento of ativos) {
    const inicioMs = Date.parse(agendamento.inicio);
    const fimMs = Date.parse(agendamento.fim);
    if (inicioMs < menorInicio) {
      menorInicio = inicioMs;
    }
    if (fimMs > maiorFim) {
      maiorFim = fimMs;
    }
  }

  const periodo = {
    de: new Date(menorInicio).toISOString(),
    ate: new Date(maiorFim).toISOString(),
  };
  const profissionalIds = Array.from(new Set(ativos.map((a) => a.profissionalId)));

  const resultados = await Promise.allSettled(
    profissionalIds.map(async (id) => [id, await listarBloqueios(id, periodo)] as const),
  );

  const mapa: Record<string, BloqueioResponse[]> = {};
  for (const resultado of resultados) {
    if (resultado.status === 'fulfilled') {
      const [profissionalId, bloqueios] = resultado.value;
      mapa[profissionalId] = bloqueios;
    }
  }
  return mapa;
}

export function PaginaDashboard() {
  const navegar = useNavigate();

  const [agendamentos, setAgendamentos] = useState<AgendamentoResponse[]>([]);
  const [bloqueiosPorProfissional, setBloqueiosPorProfissional] = useState<
    Record<string, BloqueioResponse[]>
  >({});
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<FiltroStatus>('TODOS');
  const [atualizandoId, setAtualizandoId] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const lista = await listarAgendamentos();
      const mapaBloqueios = await carregarBloqueiosDosAgendamentos(lista);
      setAgendamentos(lista);
      setBloqueiosPorProfissional(mapaBloqueios);
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível carregar os agendamentos.'));
    } finally {
      setCarregando(false);
    }
  }, [navegar]);

  useEffect(() => {
    let cancelado = false;

    async function carregarInicial() {
      try {
        const lista = await listarAgendamentos();
        const mapaBloqueios = await carregarBloqueiosDosAgendamentos(lista);
        if (!cancelado) {
          setAgendamentos(lista);
          setBloqueiosPorProfissional(mapaBloqueios);
        }
      } catch (falha) {
        if (cancelado) {
          return;
        }
        if (axios.isAxiosError(falha) && falha.response?.status === 401) {
          removerToken();
          navegar('/login', { replace: true });
          return;
        }
        setErro(extrairMensagemErro(falha, 'Não foi possível carregar os agendamentos.'));
      } finally {
        if (!cancelado) {
          setCarregando(false);
        }
      }
    }

    carregarInicial();

    return () => {
      cancelado = true;
    };
  }, [navegar]);

  async function alterarStatus(agendamentoId: string, novoStatus: StatusAgendamento) {
    setAtualizandoId(agendamentoId);
    setErro(null);
    try {
      const atualizado = await atualizarStatusAgendamento(agendamentoId, novoStatus);
      setAgendamentos((anteriores) =>
        anteriores.map((item) => (item.id === agendamentoId ? atualizado : item)),
      );
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(
        extrairMensagemErro(
          falha,
          `Não foi possível alterar o status para ${ROTULO_STATUS[novoStatus]}.`,
        ),
      );
    } finally {
      setAtualizandoId(null);
    }
  }

  function sair() {
    removerToken();
    navegar('/login', { replace: true });
  }

  const agendamentosFiltrados =
    filtro === 'TODOS'
      ? agendamentos
      : agendamentos.filter((agendamento) => agendamento.status === filtro);

  const totalConflitosBloqueio = agendamentos.filter(
    (agendamento) =>
      encontrarBloqueiosConflitantesComAgendamento(
        agendamento,
        bloqueiosPorProfissional[agendamento.profissionalId] ?? [],
      ).length > 0,
  ).length;

  const contagem = {
    total: agendamentos.length,
    pendentes: agendamentos.filter((a) => a.status === 'PENDENTE').length,
    confirmados: agendamentos.filter((a) => a.status === 'CONFIRMADO').length,
    concluidos: agendamentos.filter((a) => a.status === 'CONCLUIDO').length,
  };

  return (
    <div className={estilos.pagina}>
      <BarraPainel atual="/dashboard">
        <button type="button" className={estilos.botaoSecundario} onClick={carregar} disabled={carregando}>
          {carregando ? 'Atualizando...' : 'Atualizar'}
        </button>
        <button type="button" className={estilos.botaoSair} onClick={sair}>
          Sair
        </button>
      </BarraPainel>

      <main className={estilos.conteudo}>
        <section className={estilos.resumo} aria-label="Resumo dos agendamentos">
          <div className={estilos.cardMetrica}>
            <span className={estilos.rotuloMetrica}>Total</span>
            <strong className={estilos.valorMetrica}>{contagem.total}</strong>
          </div>
          <div className={estilos.cardMetrica}>
            <span className={estilos.rotuloMetrica}>Pendentes</span>
            <strong className={estilos.valorMetrica}>{contagem.pendentes}</strong>
          </div>
          <div className={estilos.cardMetrica}>
            <span className={estilos.rotuloMetrica}>Confirmados</span>
            <strong className={estilos.valorMetrica}>{contagem.confirmados}</strong>
          </div>
          <div className={estilos.cardMetrica}>
            <span className={estilos.rotuloMetrica}>Concluídos</span>
            <strong className={estilos.valorMetrica}>{contagem.concluidos}</strong>
          </div>
        </section>

        <section className={estilos.painel}>
          <div className={estilos.cabecalhoPainel}>
            <h1 className={estilos.tituloPainel}>Agendamentos</h1>
            <div className={estilos.filtros} role="tablist" aria-label="Filtrar por status">
              {FILTROS.map((opcao) => (
                <button
                  key={opcao.valor}
                  type="button"
                  role="tab"
                  aria-selected={filtro === opcao.valor}
                  className={`${estilos.botaoFiltro} ${
                    filtro === opcao.valor ? estilos.botaoFiltroAtivo : ''
                  }`}
                  onClick={() => setFiltro(opcao.valor)}
                >
                  {opcao.rotulo}
                </button>
              ))}
            </div>
          </div>

          {erro && (
            <div className={estilos.alertaErro} role="alert">
              {erro}
            </div>
          )}

          {!carregando && totalConflitosBloqueio > 0 && (
            <div className={estilos.alertaAviso} role="status">
              {totalConflitosBloqueio === 1
                ? '1 agendamento ativo cai dentro de um bloqueio posterior do profissional e precisa de tratamento manual.'
                : `${totalConflitosBloqueio} agendamentos ativos caem dentro de bloqueios posteriores e precisam de tratamento manual.`}
            </div>
          )}

          {carregando ? (
            <p className={estilos.estadoVazio}>Carregando agendamentos...</p>
          ) : agendamentosFiltrados.length === 0 ? (
            <p className={estilos.estadoVazio}>Nenhum agendamento encontrado para este filtro.</p>
          ) : (
            <div className={estilos.tabelaWrapper}>
              <table className={estilos.tabela}>
                <thead>
                  <tr>
                    <th>Cliente</th>
                    <th>Serviço</th>
                    <th>Profissional</th>
                    <th>Data e hora</th>
                    <th>Status</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {agendamentosFiltrados.map((agendamento) => {
                    const proximosStatus = TRANSICOES_STATUS[agendamento.status];
                    const ocupado = atualizandoId === agendamento.id;
                    const conflitosBloqueio = encontrarBloqueiosConflitantesComAgendamento(
                      agendamento,
                      bloqueiosPorProfissional[agendamento.profissionalId] ?? [],
                    );
                    const temConflitoBloqueio = conflitosBloqueio.length > 0;

                    return (
                      <tr
                        key={agendamento.id}
                        className={temConflitoBloqueio ? estilos.linhaConflitoBloqueio : undefined}
                      >
                        <td>
                          <div className={estilos.celulaPrincipal}>{agendamento.nomeCliente}</div>
                          {agendamento.emailCliente && (
                            <div className={estilos.celulaSecundaria}>{agendamento.emailCliente}</div>
                          )}
                        </td>
                        <td title={agendamento.servicoItemId}>
                          {agendamento.servicoNome ?? abreviarUuid(agendamento.servicoItemId)}
                        </td>
                        <td title={agendamento.profissionalId}>
                          {agendamento.profissionalNome ?? abreviarUuid(agendamento.profissionalId)}
                        </td>
                        <td className={estilos.dataHora}>
                          {formatarDataHoraLocal(agendamento.inicio)}
                        </td>
                        <td>
                          <div className={estilos.celulaStatus}>
                            <BadgeStatus status={agendamento.status} />
                            {agendamento.status === 'CANCELADO' &&
                              agendamento.canceladoPeloCliente && (
                                <span className={estilos.pilula}>Cancelado pelo cliente</span>
                              )}
                            {agendamento.remarcado && (
                              <span className={estilos.pilula}>Remarcado</span>
                            )}
                            {temConflitoBloqueio && (
                              <button
                                type="button"
                                className={estilos.badgeAviso}
                                title={conflitosBloqueio
                                  .map(
                                    (b) =>
                                      `${b.motivo || 'Bloqueio'} (${formatarPeriodoBloqueio(b.inicio, b.fim)})`,
                                  )
                                  .join('\n')}
                                onClick={() =>
                                  navegar(`/profissionais/${agendamento.profissionalId}/bloqueios`)
                                }
                              >
                                Conflito com bloqueio
                              </button>
                            )}
                          </div>
                        </td>
                        <td>
                          {proximosStatus.length === 0 ? (
                            <span className={estilos.semAcoes}>—</span>
                          ) : (
                            <div className={estilos.grupoAcoes}>
                              {proximosStatus.map((destino) => (
                                <button
                                  key={destino}
                                  type="button"
                                  disabled={ocupado}
                                  className={`${estilos.botaoAcao} ${
                                    destino === 'CANCELADO'
                                      ? estilos.botaoAcaoPerigo
                                      : estilos.botaoAcaoPositivo
                                  }`}
                                  onClick={() => alterarStatus(agendamento.id, destino)}
                                >
                                  {ocupado ? '...' : ROTULO_ACAO[destino]}
                                </button>
                              ))}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
