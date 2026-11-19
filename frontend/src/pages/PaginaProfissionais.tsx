import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { BadgeAtivo } from '../components/BadgeAtivo';
import { BarraPainel } from '../components/BarraPainel';
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

export function PaginaProfissionais() {
  const navegar = useNavigate();

  const [profissionais, setProfissionais] = useState<ProfissionalResponse[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [ativo, setAtivo] = useState(true);
  const [subdominioTenant, setSubdominioTenant] = useState<string | null>(null);
  const [linkCopiadoId, setLinkCopiadoId] = useState<string | null>(null);

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
    setEditandoId(null);
    setAtivo(true);
  }

  function iniciarEdicao(profissional: ProfissionalResponse) {
    setEditandoId(profissional.id);
    setNome(profissional.nome);
    setEmail(profissional.email ?? '');
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
          ativo,
        });
      } else {
        await criarProfissional({
          nome: nome.trim(),
          email: email.trim() || undefined,
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
            {profissionais.length} {profissionais.length === 1 ? 'cadastrado' : 'cadastrados'}
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
            {carregando ? (
              <p className={estilos.estadoVazio}>Carregando...</p>
            ) : profissionais.length === 0 ? (
              <p className={estilos.estadoVazio}>Nenhum profissional cadastrado.</p>
            ) : (
              <div className={estilos.listaCartoes}>
                {profissionais.map((profissional) => (
                  <article key={profissional.id} className={estilos.cartaoItem}>
                    <span className={estilos.avatar} aria-hidden="true">
                      {iniciais(profissional.nome)}
                    </span>
                    <div className={estilos.cartaoCorpo}>
                      <div className={estilos.cartaoTopo}>
                        <div>
                          <strong className={estilos.nomeItem}>{profissional.nome}</strong>
                          <span className={estilos.subItem}>{profissional.email || 'Sem e-mail'}</span>
                        </div>
                        <div className={estilos.badgesTopo}>
                          {!profissional.temDisponibilidade && (
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
                      <div className={estilos.grupoAcoes}>
                        <button
                          type="button"
                          className={estilos.botaoAcaoPositivo}
                          onClick={() => iniciarEdicao(profissional)}
                        >
                          Editar
                        </button>
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
                        <button
                          type="button"
                          className={profissional.ativo ? estilos.botaoAcaoPerigo : estilos.botaoAcaoPositivo}
                          disabled={salvando}
                          onClick={() => alterarStatus(profissional, !profissional.ativo)}
                        >
                          {profissional.ativo ? 'Desativar' : 'Reativar'}
                        </button>
                      </div>
                      {!profissional.temDisponibilidade && (
                        <p className={estilos.avisoDiscreto}>
                          O link público abrirá sem horários disponíveis até configurar a agenda.
                        </p>
                      )}
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

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/).slice(0, 2);
  return partes.map((parte) => parte.charAt(0).toUpperCase()).join('') || '?';
}
