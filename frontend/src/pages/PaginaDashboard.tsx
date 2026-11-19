import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { BadgeStatus } from '../components/BadgeStatus';
import {
  atualizarStatusAgendamento,
  listarAgendamentos,
} from '../services/agendamentoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import { ROTULO_STATUS, TRANSICOES_STATUS } from '../types/dominio';
import type { AgendamentoResponse, StatusAgendamento } from '../types/dominio';
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

export function PaginaDashboard() {
  const navegar = useNavigate();

  const [agendamentos, setAgendamentos] = useState<AgendamentoResponse[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<FiltroStatus>('TODOS');
  const [atualizandoId, setAtualizandoId] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const lista = await listarAgendamentos();
      setAgendamentos(lista);
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
        if (!cancelado) {
          setAgendamentos(lista);
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

  const contagem = {
    total: agendamentos.length,
    pendentes: agendamentos.filter((a) => a.status === 'PENDENTE').length,
    confirmados: agendamentos.filter((a) => a.status === 'CONFIRMADO').length,
    concluidos: agendamentos.filter((a) => a.status === 'CONCLUIDO').length,
  };

  return (
    <div className={estilos.pagina}>
      <header className={estilos.barraSuperior}>
        <div className={estilos.barraConteudo}>
          <div className={estilos.identidade}>
            <span className={estilos.marca}>NoPonto</span>
            <span className={estilos.separador}>/</span>
            <span className={estilos.tituloPagina}>Dashboard Administrativo</span>
          </div>
          <div className={estilos.acoesBarra}>
            <button
              type="button"
              className={estilos.botaoSecundario}
              onClick={() => navegar('/profissionais')}
            >
              Profissionais
            </button>
            <button
              type="button"
              className={estilos.botaoSecundario}
              onClick={carregar}
              disabled={carregando}
            >
              {carregando ? 'Atualizando...' : 'Atualizar'}
            </button>
            <button type="button" className={estilos.botaoSair} onClick={sair}>
              Sair
            </button>
          </div>
        </div>
      </header>

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

                    return (
                      <tr key={agendamento.id}>
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
                          <BadgeStatus status={agendamento.status} />
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
