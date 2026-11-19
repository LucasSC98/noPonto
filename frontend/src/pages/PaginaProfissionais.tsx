import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { BadgeAtivo } from '../components/BadgeAtivo';
import { BarraPainel } from '../components/BarraPainel';
import { MenuAcoes } from '../components/MenuAcoes';
import {
  atualizarProfissional,
  atualizarStatusProfissional,
  criarProfissional,
  listarProfissionais,
  obterTenantAtual,
} from '../services/gestaoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import type { ProfissionalResponse } from '../types/gestao';
import estilos from './PaginaDashboard.module.css';

type FiltroStatusProfissional = 'TODOS' | 'ATIVOS' | 'INATIVOS';

export function PaginaProfissionais() {
  const navegar = useNavigate();

  const [profissionais, setProfissionais] = useState<ProfissionalResponse[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [especialidade, setEspecialidade] = useState('');
  const [telefone, setTelefone] = useState('');
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [ativo, setAtivo] = useState(true);
  const [subdominioTenant, setSubdominioTenant] = useState<string | null>(null);
  const [linkCopiadoId, setLinkCopiadoId] = useState<string | null>(null);

  const [busca, setBusca] = useState('');
  const [buscaDebounced, setBuscaDebounced] = useState('');
  const [filtroStatus, setFiltroStatus] = useState<FiltroStatusProfissional>('TODOS');

  useEffect(() => {
    const temporizador = window.setTimeout(() => {
      setBuscaDebounced(busca.trim().toLowerCase());
    }, 300);
    return () => window.clearTimeout(temporizador);
  }, [busca]);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const [pagina, tenant] = await Promise.all([listarProfissionais(0, 50), obterTenantAtual()]);
      setProfissionais(pagina.content);
      setSubdominioTenant(tenant.subdominio);
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível carregar os profissionais.'));
    } finally {
      setCarregando(false);
    }
  }, [navegar]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function copiarLinkAgendamento(profissionalId: string) {
    if (!subdominioTenant) {
      setErro('Não foi possível identificar o estabelecimento para montar o link.');
      return;
    }

    const url = `${window.location.origin}/agendar/${profissionalId}?tenant=${encodeURIComponent(subdominioTenant)}`;
    try {
      await navigator.clipboard.writeText(url);
      setLinkCopiadoId(profissionalId);
      window.setTimeout(() => {
        setLinkCopiadoId((atual) => (atual === profissionalId ? null : atual));
      }, 2000);
    } catch {
      setErro('Não foi possível copiar o link de agendamento.');
    }
  }

  async function alterarStatus(profissional: ProfissionalResponse, ativo: boolean) {
    if (!ativo) {
      const confirmado = window.confirm(
        `Desativar ${profissional.nome}? Os agendamentos já feitos permanecem no histórico.`,
      );
      if (!confirmado) {
        return;
      }
    }

    setSalvando(true);
    setErro(null);
    try {
      await atualizarStatusProfissional(profissional.id, ativo);
      if (editandoId === profissional.id) {
        setAtivo(ativo);
      }
      await carregar();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível atualizar o status do profissional.'));
    } finally {
      setSalvando(false);
    }
  }

  function limparFormulario() {
    setNome('');
    setEmail('');
    setEspecialidade('');
    setTelefone('');
    setEditandoId(null);
    setAtivo(true);
  }

  function iniciarEdicao(profissional: ProfissionalResponse) {
    setEditandoId(profissional.id);
    setNome(profissional.nome);
    setEmail(profissional.email ?? '');
    setEspecialidade(profissional.especialidade ?? '');
    setTelefone(profissional.telefone ?? '');
    setAtivo(profissional.ativo);
  }

  async function aoEnviar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);

    try {
      if (editandoId) {
        await atualizarProfissional(editandoId, {
          nome: nome.trim(),
          email: email.trim() || undefined,
          especialidade: especialidade.trim() || undefined,
          telefone: telefone.trim() || undefined,
          ativo,
        });
      } else {
        await criarProfissional({
          nome: nome.trim(),
          email: email.trim() || undefined,
          especialidade: especialidade.trim() || undefined,
          telefone: telefone.trim() || undefined,
        });
      }
      limparFormulario();
      await carregar();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível salvar o profissional.'));
    } finally {
      setSalvando(false);
    }
  }

  const contagemStatus = useMemo(() => {
    const ativos = profissionais.filter((p) => p.ativo).length;
    return {
      todos: profissionais.length,
      ativos,
      inativos: profissionais.length - ativos,
    };
  }, [profissionais]);

  const profissionaisFiltrados = useMemo(() => {
    return profissionais.filter((profissional) => {
      if (filtroStatus === 'ATIVOS' && !profissional.ativo) {
        return false;
      }
      if (filtroStatus === 'INATIVOS' && profissional.ativo) {
        return false;
      }
      if (!buscaDebounced) {
        return true;
      }
      const nomeMatch = profissional.nome.toLowerCase().includes(buscaDebounced);
      const emailMatch = (profissional.email ?? '').toLowerCase().includes(buscaDebounced);
      return nomeMatch || emailMatch;
    });
  }, [profissionais, filtroStatus, buscaDebounced]);

  return (
    <div className={estilos.pagina}>
      <BarraPainel atual="/profissionais" />

      <main className={estilos.conteudo}>
        <div className={estilos.introducao}>
          <div>
            <h1 className={estilos.tituloGrande}>Profissionais</h1>
            <p className={estilos.descricao}>
              Cadastre quem atende, defina os horários e copie o link público de agendamento.
            </p>
          </div>
          <span className={estilos.contador}>
            {profissionaisFiltrados.length}{' '}
            {profissionaisFiltrados.length === 1 ? 'cadastrado' : 'cadastrados'}
          </span>
        </div>

        {erro && <div className={estilos.alertaErro}>{erro}</div>}

        <div className={estilos.gradeCadastro}>
          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>{editandoId ? 'Editar profissional' : 'Novo profissional'}</h2>
            <form className={estilos.formulario} onSubmit={aoEnviar}>
              <label className={estilos.campo}>
                <span className={estilos.rotulo}>Nome</span>
                <input
                  type="text"
                  value={nome}
                  onChange={(evento) => setNome(evento.target.value)}
                  required
                  placeholder="Nome completo"
                  className={estilos.entrada}
                />
              </label>
              <label className={estilos.campo}>
                <span className={estilos.rotulo}>E-mail (opcional)</span>
                <input
                  type="email"
                  value={email}
                  onChange={(evento) => setEmail(evento.target.value)}
                  placeholder="nome@clinica.com"
                  className={estilos.entrada}
                />
              </label>
              <label className={estilos.campo}>
                <span className={estilos.rotulo}>Especialidade (opcional)</span>
                <input
                  type="text"
                  maxLength={80}
                  value={especialidade}
                  onChange={(evento) => setEspecialidade(evento.target.value)}
                  placeholder="Ex.: Clínico Geral"
                  className={estilos.entrada}
                />
              </label>
              <label className={estilos.campo}>
                <span className={estilos.rotulo}>Telefone (opcional)</span>
                <input
                  type="tel"
                  maxLength={20}
                  value={telefone}
                  onChange={(evento) => setTelefone(evento.target.value)}
                  placeholder="(11) 99999-9999"
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
          </section>

          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>Equipe</h2>
            <div className={estilos.barraFiltros}>
              <input
                type="search"
                value={busca}
                onChange={(evento) => setBusca(evento.target.value)}
                placeholder="Buscar por nome ou e-mail..."
                aria-label="Buscar profissional por nome ou e-mail"
                className={estilos.campoBusca}
              />
              <div className={estilos.filtros} role="tablist" aria-label="Filtrar profissionais por status">
                {(
                  [
                    { valor: 'TODOS', rotulo: `Todos (${contagemStatus.todos})` },
                    { valor: 'ATIVOS', rotulo: `Ativos (${contagemStatus.ativos})` },
                    { valor: 'INATIVOS', rotulo: `Inativos (${contagemStatus.inativos})` },
                  ] as const
                ).map((opcao) => (
                  <button
                    key={opcao.valor}
                    type="button"
                    role="tab"
                    aria-selected={filtroStatus === opcao.valor}
                    className={`${estilos.botaoFiltro} ${
                      filtroStatus === opcao.valor ? estilos.botaoFiltroAtivo : ''
                    }`}
                    onClick={() => setFiltroStatus(opcao.valor)}
                  >
                    {opcao.rotulo}
                  </button>
                ))}
              </div>
            </div>
            {carregando ? (
              <p className={estilos.estadoVazio}>Carregando...</p>
            ) : profissionaisFiltrados.length === 0 ? (
              <p className={estilos.estadoVazio}>Nenhum profissional encontrado</p>
            ) : (
              <div className={estilos.listaCartoes}>
                {profissionaisFiltrados.map((profissional) => {
                  const servicosAtivos = profissional.servicosAtivos ?? [];
                  const quantidadeServicos = profissional.quantidadeServicosAtivos ?? servicosAtivos.length;
                  const agendamentosSemana = profissional.agendamentosSemana ?? 0;
                  const semDisponibilidade = profissional.temDisponibilidade === false;

                  return (
                  <article key={profissional.id} className={estilos.cartaoItem}>
                    <span className={estilos.avatar} aria-hidden="true">
                      {iniciais(profissional.nome)}
                    </span>
                    <div className={estilos.cartaoCorpo}>
                      <div className={estilos.cartaoTopo}>
                        <div>
                          <strong className={estilos.nomeItem}>{profissional.nome}</strong>
                          {profissional.especialidade && (
                            <span className={estilos.subItem}>{profissional.especialidade}</span>
                          )}
                          <span className={estilos.subItem}>{profissional.email || 'Sem e-mail'}</span>
                          <span className={estilos.subItem}>
                            {formatarResumoProfissional(quantidadeServicos, agendamentosSemana)}
                          </span>
                        </div>
                        <div className={estilos.badgesTopo}>
                          {semDisponibilidade && (
                            <button
                              type="button"
                              className={estilos.badgeAviso}
                              onClick={() => navegar(`/profissionais/${profissional.id}/disponibilidade`)}
                            >
                              Sem horários configurados
                            </button>
                          )}
                          <BadgeAtivo ativo={profissional.ativo} />
                        </div>
                      </div>
                      <div className={estilos.listaChips}>
                        {servicosAtivos.slice(0, 3).map((servico) => (
                          <span key={servico.id} className={estilos.pilula}>
                            {servico.nome} · {servico.duracaoMinutos}min
                          </span>
                        ))}
                        {servicosAtivos.length > 3 && (
                          <span
                            className={estilos.chipExtra}
                            title={servicosAtivos
                              .map((servico) => `${servico.nome} · ${servico.duracaoMinutos}min`)
                              .join('\n')}
                          >
                            +{servicosAtivos.length - 3}
                          </span>
                        )}
                        <button
                          type="button"
                          className={estilos.linkGerenciarServicos}
                          onClick={() => navegar(`/servicos?profissionalId=${profissional.id}`)}
                        >
                          Gerenciar serviços
                        </button>
                      </div>
                      <div className={estilos.grupoAcoes}>
                        <button
                          type="button"
                          className={estilos.botaoAcaoPositivo}
                          onClick={() => navegar(`/profissionais/${profissional.id}/disponibilidade`)}
                        >
                          Horários
                        </button>
                        <button
                          type="button"
                          className={estilos.botaoAcaoPositivo}
                          onClick={() => copiarLinkAgendamento(profissional.id)}
                        >
                          {linkCopiadoId === profissional.id ? 'Link copiado!' : 'Copiar link'}
                        </button>
                        <MenuAcoes
                          rotuloAcessivel={`Mais ações para ${profissional.nome}`}
                          itens={[
                            {
                              rotulo: 'Editar',
                              aoClicar: () => iniciarEdicao(profissional),
                            },
                            {
                              rotulo: profissional.ativo ? 'Desativar' : 'Reativar',
                              aoClicar: () => alterarStatus(profissional, !profissional.ativo),
                              perigo: profissional.ativo,
                              desabilitado: salvando,
                            },
                          ]}
                        />
                      </div>
                      {semDisponibilidade && (
                        <p className={estilos.avisoDiscreto}>
                          O link público abrirá sem horários disponíveis até configurar a agenda.
                        </p>
                      )}
                    </div>
                  </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

function formatarResumoProfissional(servicosAtivos: number, agendamentosSemana: number): string {
  const textoServicos =
    servicosAtivos === 1 ? '1 serviço ativo' : `${servicosAtivos} serviços ativos`;
  const textoAgendamentos =
    agendamentosSemana === 1
      ? '1 agendamento esta semana'
      : `${agendamentosSemana} agendamentos esta semana`;
  return `${textoServicos} · ${textoAgendamentos}`;
}

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/).slice(0, 2);
  return partes.map((parte) => parte.charAt(0).toUpperCase()).join('') || '?';
}
