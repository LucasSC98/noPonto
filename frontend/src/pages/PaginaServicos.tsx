import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { BadgeAtivo } from '../components/BadgeAtivo';
import {
  atualizarServico,
  atualizarStatusServico,
  criarServico,
  listarProfissionais,
  listarServicos,
} from '../services/gestaoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import type { ProfissionalResponse, ServicoItemResponse } from '../types/gestao';
import estilos from './PaginaDashboard.module.css';

export function PaginaServicos() {
  const navegar = useNavigate();

  const [profissionais, setProfissionais] = useState<ProfissionalResponse[]>([]);
  const [profissionalSelecionado, setProfissionalSelecionado] = useState<string>('');

  const [servicos, setServicos] = useState<ServicoItemResponse[]>([]);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const [nome, setNome] = useState('');
  const [duracaoMinutos, setDuracaoMinutos] = useState<number>(30);
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [ativo, setAtivo] = useState(true);

  const carregarProfissionais = useCallback(async () => {
    try {
      const pagina = await listarProfissionais(0, 100);
      setProfissionais(pagina.content);
      if (pagina.content.length > 0 && !profissionalSelecionado) {
        setProfissionalSelecionado(pagina.content[0].id);
      }
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
      }
    }
  }, [navegar, profissionalSelecionado]);

  const carregarServicos = useCallback(async () => {
    if (!profissionalSelecionado) {
      setServicos([]);
      return;
    }

    setCarregando(true);
    setErro(null);
    try {
      const pagina = await listarServicos(profissionalSelecionado, 0, 50);
      setServicos(pagina.content);
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível carregar os serviços.'));
    } finally {
      setCarregando(false);
    }
  }, [profissionalSelecionado, navegar]);

  useEffect(() => {
    carregarProfissionais();
  }, [carregarProfissionais]);

  useEffect(() => {
    carregarServicos();
  }, [carregarServicos]);

  function limparFormulario() {
    setNome('');
    setDuracaoMinutos(30);
    setEditandoId(null);
    setAtivo(true);
  }

  function iniciarEdicao(servico: ServicoItemResponse) {
    setEditandoId(servico.id);
    setNome(servico.nome);
    setDuracaoMinutos(servico.duracaoMinutos);
    setAtivo(servico.ativo);
  }

  async function alterarStatus(servico: ServicoItemResponse, proximoAtivo: boolean) {
    if (!proximoAtivo) {
      const confirmado = window.confirm(
        `Desativar ${servico.nome}? Os agendamentos já feitos permanecem no histórico.`,
      );
      if (!confirmado) {
        return;
      }
    }

    setSalvando(true);
    setErro(null);
    try {
      await atualizarStatusServico(servico.id, proximoAtivo);
      if (editandoId === servico.id) {
        setAtivo(proximoAtivo);
      }
      await carregarServicos();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível atualizar o status do serviço.'));
    } finally {
      setSalvando(false);
    }
  }

  async function aoEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (!profissionalSelecionado) {
      setErro('Selecione um profissional.');
      return;
    }

    setSalvando(true);
    setErro(null);

    try {
      if (editandoId) {
        await atualizarServico(editandoId, {
          nome: nome.trim(),
          duracaoMinutos,
          ativo,
        });
      } else {
        await criarServico({
          profissionalId: profissionalSelecionado,
          nome: nome.trim(),
          duracaoMinutos,
        });
      }
      limparFormulario();
      await carregarServicos();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível salvar o serviço.'));
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className={estilos.pagina}>
      <header className={estilos.barraSuperior}>
        <div className={estilos.barraConteudo}>
          <div className={estilos.identidade}>
            <span className={estilos.marca}>NoPonto</span>
            <span className={estilos.separador}>/</span>
            <span className={estilos.tituloPagina}>Serviços</span>
          </div>
          <div className={estilos.acoesBarra}>
            <button type="button" className={estilos.botaoSecundario} onClick={() => navegar('/dashboard')}>
              Voltar ao dashboard
            </button>
          </div>
        </div>
      </header>

      <main className={estilos.conteudo}>
        <section className={estilos.painel}>
          <h1 className={estilos.tituloPainel}>Gerenciar serviços</h1>

          <label>
            Profissional
            <select
              value={profissionalSelecionado}
              onChange={(e) => {
                setProfissionalSelecionado(e.target.value);
                limparFormulario();
              }}
              style={{ width: '100%', maxWidth: 400, padding: '0.5rem', marginTop: 4 }}
            >
              <option value="">Selecione...</option>
              {profissionais.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome} {p.ativo ? '' : '(inativo)'}
                </option>
              ))}
            </select>
          </label>

          {profissionalSelecionado && (
            <form onSubmit={aoEnviar} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: 480, marginTop: '1.5rem' }}>
              <h2 style={{ margin: 0, fontSize: '1.1rem' }}>
                {editandoId ? 'Editar serviço' : 'Novo serviço'}
              </h2>

              <label>
                Nome do serviço
                <input
                  type="text"
                  value={nome}
                  onChange={(e) => setNome(e.target.value)}
                  required
                  style={{ width: '100%', padding: '0.5rem', marginTop: 4 }}
                />
              </label>

              <label>
                Duração (minutos)
                <input
                  type="number"
                  min={5}
                  step={5}
                  value={duracaoMinutos}
                  onChange={(e) => setDuracaoMinutos(Number(e.target.value))}
                  required
                  style={{ width: '100%', padding: '0.5rem', marginTop: 4 }}
                />
              </label>

              <div style={{ display: 'flex', gap: 8 }}>
                <button type="submit" disabled={salvando} className={estilos.botaoSecundario}>
                  {salvando ? 'Salvando...' : editandoId ? 'Atualizar' : 'Cadastrar'}
                </button>
                {editandoId && (
                  <button type="button" onClick={limparFormulario} className={estilos.botaoSecundario}>
                    Cancelar
                  </button>
                )}
              </div>
            </form>
          )}

          {erro && <div className={estilos.alertaErro} style={{ marginTop: '1rem' }}>{erro}</div>}
        </section>

        {profissionalSelecionado && (
          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>Serviços do profissional</h2>

            {carregando ? (
              <p className={estilos.estadoVazio}>Carregando...</p>
            ) : servicos.length === 0 ? (
              <p className={estilos.estadoVazio}>Nenhum serviço cadastrado para este profissional.</p>
            ) : (
              <div className={estilos.tabelaWrapper}>
                <table className={estilos.tabela}>
                  <thead>
                    <tr>
                      <th>Nome</th>
                      <th>Duração</th>
                      <th>Status</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {servicos.map((s) => (
                      <tr key={s.id}>
                        <td>{s.nome}</td>
                        <td>{s.duracaoMinutos} min</td>
                        <td><BadgeAtivo ativo={s.ativo} /></td>
                        <td>
                          <div className={estilos.grupoAcoes}>
                            <button type="button" className={estilos.botaoAcaoPositivo} onClick={() => iniciarEdicao(s)}>
                              Editar
                            </button>
                            <button
                              type="button"
                              className={s.ativo ? estilos.botaoAcaoPerigo : estilos.botaoAcaoPositivo}
                              disabled={salvando}
                              onClick={() => alterarStatus(s, !s.ativo)}
                            >
                              {s.ativo ? 'Desativar' : 'Reativar'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  );
}
