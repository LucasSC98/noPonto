import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  atualizarProfissional,
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
      <header className={estilos.barraSuperior}>
        <div className={estilos.barraConteudo}>
          <div className={estilos.identidade}>
            <span className={estilos.marca}>NoPonto</span>
            <span className={estilos.separador}>/</span>
            <span className={estilos.tituloPagina}>Profissionais</span>
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
          <h1 className={estilos.tituloPainel}>
            {editandoId ? 'Editar profissional' : 'Novo profissional'}
          </h1>

          <form onSubmit={aoEnviar} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: 480 }}>
            <label>
              Nome
              <input
                type="text"
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                required
                style={{ width: '100%', padding: '0.5rem', marginTop: 4 }}
              />
            </label>

            <label>
              E-mail (opcional)
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{ width: '100%', padding: '0.5rem', marginTop: 4 }}
              />
            </label>

            {editandoId && (
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input type="checkbox" checked={ativo} onChange={(e) => setAtivo(e.target.checked)} />
                Ativo
              </label>
            )}

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

          {erro && <div className={estilos.alertaErro}>{erro}</div>}
        </section>

        <section className={estilos.painel}>
          <h2 className={estilos.tituloPainel}>Lista de profissionais</h2>

          {carregando ? (
            <p className={estilos.estadoVazio}>Carregando...</p>
          ) : profissionais.length === 0 ? (
            <p className={estilos.estadoVazio}>Nenhum profissional cadastrado.</p>
          ) : (
            <div className={estilos.tabelaWrapper}>
              <table className={estilos.tabela}>
                <thead>
                  <tr>
                    <th>Nome</th>
                    <th>E-mail</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {profissionais.map((p) => (
                    <tr key={p.id}>
                      <td>{p.nome}</td>
                      <td>{p.email || '—'}</td>
                      <td>{p.ativo ? 'Ativo' : 'Inativo'}</td>
                      <td>
                        <div className={estilos.grupoAcoes}>
                          <button type="button" className={estilos.botaoAcaoPositivo} onClick={() => iniciarEdicao(p)}>
                            Editar
                          </button>
                          <button
                            type="button"
                            className={estilos.botaoAcaoPositivo}
                            onClick={() => navegar(`/profissionais/${p.id}/disponibilidade`)}
                          >
                            Horários
                          </button>
                          <button
                            type="button"
                            className={estilos.botaoAcaoPositivo}
                            onClick={() => copiarLinkAgendamento(p.id)}
                          >
                            {linkCopiadoId === p.id ? 'Link copiado!' : 'Copiar link de agendamento'}
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
      </main>
    </div>
  );
}
