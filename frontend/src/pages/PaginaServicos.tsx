import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { BadgeAtivo } from '../components/BadgeAtivo';
import { BarraPainel } from '../components/BarraPainel';
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
  const [searchParams, setSearchParams] = useSearchParams();
  const profissionalIdUrl = searchParams.get('profissionalId') ?? '';

  const [profissionais, setProfissionais] = useState<ProfissionalResponse[]>([]);
  const [profissionalSelecionado, setProfissionalSelecionado] = useState<string>(profissionalIdUrl);

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

  const profissionalAtual = profissionais.find((item) => item.id === profissionalSelecionado);

  return (
    <div className={estilos.pagina}>
      <BarraPainel atual="/servicos" />

      <main className={estilos.conteudo}>
        <div className={estilos.introducao}>
          <div>
            <h1 className={estilos.tituloGrande}>Serviços</h1>
            <p className={estilos.descricao}>
              Cada serviço tem uma duração. Ela define o tamanho dos horários oferecidos na agenda pública.
            </p>
          </div>
          <span className={estilos.contador}>
            {servicos.length} {servicos.length === 1 ? 'serviço' : 'serviços'}
          </span>
        </div>

        {erro && <div className={estilos.alertaErro}>{erro}</div>}

        <div className={estilos.gradeCadastro}>
          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>{editandoId ? 'Editar serviço' : 'Novo serviço'}</h2>
            <label className={estilos.campo}>
              <span className={estilos.rotulo}>Profissional</span>
              <select
                value={profissionalSelecionado}
                onChange={(evento) => {
                  const novoId = evento.target.value;
                  setProfissionalSelecionado(novoId);
                  if (novoId) {
                    setSearchParams({ profissionalId: novoId }, { replace: true });
                  } else {
                    setSearchParams({}, { replace: true });
                  }
                  limparFormulario();
                }}
                className={estilos.entrada}
              >
                <option value="">Selecione...</option>
                {profissionais.map((profissional) => (
                  <option key={profissional.id} value={profissional.id}>
                    {profissional.nome}
                    {profissional.ativo ? '' : ' (inativo)'}
                  </option>
                ))}
              </select>
            </label>

            {profissionalSelecionado && (
              <form className={estilos.formulario} onSubmit={aoEnviar}>
                <label className={estilos.campo}>
                  <span className={estilos.rotulo}>Nome do serviço</span>
                  <input
                    type="text"
                    value={nome}
                    onChange={(evento) => setNome(evento.target.value)}
                    required
                    placeholder="Consulta, retorno, avaliação..."
                    className={estilos.entrada}
                  />
                </label>
                <label className={estilos.campo}>
                  <span className={estilos.rotulo}>Duração (minutos)</span>
                  <input
                    type="number"
                    min={5}
                    step={5}
                    value={duracaoMinutos}
                    onChange={(evento) => setDuracaoMinutos(Number(evento.target.value))}
                    required
                    className={estilos.entrada}
                  />
                </label>
                <div className={estilos.linhaBotoes}>
                  <button type="submit" disabled={salvando} className={estilos.botaoPrimario}>
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
          </section>

          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>
              {profissionalAtual ? `Serviços de ${profissionalAtual.nome}` : 'Serviços'}
            </h2>
            {!profissionalSelecionado ? (
              <p className={estilos.estadoVazio}>Selecione um profissional para ver os serviços.</p>
            ) : carregando ? (
              <p className={estilos.estadoVazio}>Carregando...</p>
            ) : servicos.length === 0 ? (
              <p className={estilos.estadoVazio}>Nenhum serviço cadastrado para este profissional.</p>
            ) : (
              <div className={estilos.listaCartoes}>
                {servicos.map((servico) => (
                  <article key={servico.id} className={estilos.cartaoItem}>
                    <span className={`${estilos.avatar} ${estilos.avatarNumero}`} aria-hidden="true">
                      {servico.duracaoMinutos}
                    </span>
                    <div className={estilos.cartaoCorpo}>
                      <div className={estilos.cartaoTopo}>
                        <div>
                          <strong className={estilos.nomeItem}>{servico.nome}</strong>
                          <span className={estilos.subItem}>
                            <span className={estilos.pilula}>{servico.duracaoMinutos} min</span>
                          </span>
                        </div>
                        <BadgeAtivo ativo={servico.ativo} />
                      </div>
                      <div className={estilos.grupoAcoes}>
                        <button type="button" className={estilos.botaoAcaoPositivo} onClick={() => iniciarEdicao(servico)}>
                          Editar
                        </button>
                        <button
                          type="button"
                          className={servico.ativo ? estilos.botaoAcaoPerigo : estilos.botaoAcaoPositivo}
                          disabled={salvando}
                          onClick={() => alterarStatus(servico, !servico.ativo)}
                        >
                          {servico.ativo ? 'Desativar' : 'Reativar'}
                        </button>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
