import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import { BadgeStatus } from '../components/BadgeStatus';
import { BarraPainel } from '../components/BarraPainel';
import {
  atualizarBloqueio,
  criarBloqueio,
  listarBloqueios,
  listarProfissionais,
  removerBloqueio,
} from '../services/gestaoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import type {
  AgendamentoConflitanteResponse,
  BloqueioResponse,
  ConflitoBloqueioResponse,
  SalvarBloqueioRequest,
} from '../types/gestao';
import { dataHojeIsoLocal, formatarDataHoraLocal } from '../utils/datas';
import {
  bloqueioParaFormulario,
  ehBloqueioDiaInteiro,
  formatarPeriodoBloqueio,
  montarIntervaloBloqueioUtc,
  separarBloqueiosFuturosEHistorico,
  TAMANHO_MAXIMO_MOTIVO_BLOQUEIO,
  unirBloqueiosSobrepostos,
  validarFormularioBloqueio,
  type DadosFormularioBloqueio,
  type ErrosFormularioBloqueio,
} from '../utils/bloqueios';
import estilos from './PaginaDashboard.module.css';

interface ConflitoPendente {
  mensagem: string;
  agendamentos: AgendamentoConflitanteResponse[];
  payload: SalvarBloqueioRequest;
  edicaoId: string | null;
}

function criarFormularioInicial(): DadosFormularioBloqueio {
  const hoje = dataHojeIsoLocal();
  return {
    dataInicio: hoje,
    dataFim: hoje,
    diaInteiro: true,
    horaInicio: '08:00',
    horaFim: '18:00',
    motivo: '',
  };
}

export function PaginaBloqueios() {
  const { profissionalId } = useParams<{ profissionalId: string }>();
  const navegar = useNavigate();

  const [nomeProfissional, setNomeProfissional] = useState('Profissional');
  const [bloqueios, setBloqueios] = useState<BloqueioResponse[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const [formulario, setFormulario] = useState<DadosFormularioBloqueio>(criarFormularioInicial);
  const [errosCampos, setErrosCampos] = useState<ErrosFormularioBloqueio>({});
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [historicoAberto, setHistoricoAberto] = useState(false);
  const [conflitoPendente, setConflitoPendente] = useState<ConflitoPendente | null>(null);

  const recarregar = useCallback(async () => {
    if (!profissionalId) {
      return;
    }
    setCarregando(true);
    setErro(null);
    try {
      const [listaBloqueios, paginaProfissionais] = await Promise.all([
        listarBloqueios(profissionalId),
        listarProfissionais(0, 100),
      ]);
      setBloqueios(listaBloqueios ?? []);
      const profissional = paginaProfissionais.content.find((item) => item.id === profissionalId);
      if (profissional) {
        setNomeProfissional(profissional.nome);
      }
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível carregar os bloqueios.'));
    } finally {
      setCarregando(false);
    }
  }, [profissionalId, navegar]);

  useEffect(() => {
    let cancelado = false;

    async function carregarInicial() {
      if (!profissionalId) {
        setErro('Profissional não informado.');
        setCarregando(false);
        return;
      }

      try {
        const [listaBloqueios, paginaProfissionais] = await Promise.all([
          listarBloqueios(profissionalId),
          listarProfissionais(0, 100),
        ]);
        if (cancelado) {
          return;
        }
        setBloqueios(listaBloqueios ?? []);
        const profissional = paginaProfissionais.content.find((item) => item.id === profissionalId);
        if (profissional) {
          setNomeProfissional(profissional.nome);
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
        setErro(extrairMensagemErro(falha, 'Não foi possível carregar os bloqueios.'));
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
  }, [profissionalId, navegar]);

  useEffect(() => {
    if (!conflitoPendente) {
      return;
    }
    function aoPressionarTecla(evento: KeyboardEvent) {
      if (evento.key === 'Escape' && !salvando) {
        setConflitoPendente(null);
      }
    }
    window.addEventListener('keydown', aoPressionarTecla);
    return () => window.removeEventListener('keydown', aoPressionarTecla);
  }, [conflitoPendente, salvando]);

  function limparFormulario() {
    setFormulario(criarFormularioInicial());
    setErrosCampos({});
    setEditandoId(null);
  }

  function iniciarEdicao(bloqueio: BloqueioResponse) {
    setEditandoId(bloqueio.id);
    setFormulario(bloqueioParaFormulario(bloqueio));
    setErrosCampos({});
    setErro(null);
  }

  function atualizarCampo<K extends keyof DadosFormularioBloqueio>(
    campo: K,
    valor: DadosFormularioBloqueio[K],
  ) {
    setFormulario((anterior) => {
      const proximo = { ...anterior, [campo]: valor };
      if (campo === 'dataInicio' && typeof valor === 'string' && proximo.dataFim < valor) {
        proximo.dataFim = valor;
      }
      return proximo;
    });
    if (campo in errosCampos) {
      setErrosCampos((anteriores) => ({ ...anteriores, [campo]: undefined }));
    }
  }

  async function persistirBloqueio(payload: SalvarBloqueioRequest, alvoEdicaoId: string | null) {
    if (!profissionalId) {
      return;
    }

    setSalvando(true);
    setErro(null);
    try {
      if (alvoEdicaoId) {
        await atualizarBloqueio(profissionalId, alvoEdicaoId, payload);
      } else {
        await criarBloqueio(profissionalId, payload);
      }
      setConflitoPendente(null);
      limparFormulario();
      await recarregar();
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }

      if (axios.isAxiosError<ConflitoBloqueioResponse>(falha) && falha.response?.status === 409) {
        const dadosConflito = falha.response.data;
        const agendamentos = dadosConflito?.agendamentosConflitantes ?? [];
        if (agendamentos.length > 0) {
          setConflitoPendente({
            mensagem:
              dadosConflito.mensagem ||
              'Existem agendamentos ativos neste período. Deseja criar o bloqueio mesmo assim?',
            agendamentos,
            payload,
            edicaoId: alvoEdicaoId,
          });
          return;
        }
      }

      setConflitoPendente(null);
      setErro(extrairMensagemErro(falha, 'Não foi possível salvar o bloqueio.'));
    } finally {
      setSalvando(false);
    }
  }

  async function aoEnviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);

    const validacao = validarFormularioBloqueio(formulario);
    setErrosCampos(validacao.erros);
    if (!validacao.valido) {
      return;
    }

    const { inicio, fim } = montarIntervaloBloqueioUtc(formulario);
    const motivoLimpo = formulario.motivo.trim();
    const payload: SalvarBloqueioRequest = {
      inicio,
      fim,
      motivo: motivoLimpo.length > 0 ? motivoLimpo : null,
    };

    await persistirBloqueio(payload, editandoId);
  }

  async function confirmarBloqueioComConflito() {
    if (!conflitoPendente) {
      return;
    }
    await persistirBloqueio(
      { ...conflitoPendente.payload, confirmarMesmoComConflito: true },
      conflitoPendente.edicaoId,
    );
  }

  async function aoRemover(bloqueio: BloqueioResponse) {
    if (!profissionalId) {
      return;
    }

    const descricao = formatarPeriodoBloqueio(bloqueio.inicio, bloqueio.fim);
    const confirmado = window.confirm(
      `Remover o bloqueio "${bloqueio.motivo || descricao}"? Os horários desse período voltarão a ficar disponíveis.`,
    );
    if (!confirmado) {
      return;
    }

    setSalvando(true);
    setErro(null);
    try {
      await removerBloqueio(profissionalId, bloqueio.id);
      if (editandoId === bloqueio.id) {
        limparFormulario();
      }
      await recarregar();
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível remover o bloqueio.'));
    } finally {
      setSalvando(false);
    }
  }

  const { futuros, historico } = useMemo(
    () => separarBloqueiosFuturosEHistorico(bloqueios),
    [bloqueios],
  );
  const gruposFuturos = useMemo(() => unirBloqueiosSobrepostos(futuros), [futuros]);

  return (
    <div className={estilos.pagina}>
      <BarraPainel atual="/profissionais">
        <button
          type="button"
          className={estilos.botaoSecundario}
          onClick={() => navegar('/profissionais')}
        >
          Voltar para profissionais
        </button>
      </BarraPainel>

      <main className={estilos.conteudo}>
        <div className={estilos.introducao}>
          <div>
            <h1 className={estilos.tituloGrande}>Bloqueios de {nomeProfissional}</h1>
            <p className={estilos.descricao}>
              Registre folgas, feriados, férias e indisponibilidades pontuais. Horários bloqueados
              deixam de aparecer para novos agendamentos.
            </p>
          </div>
          <span className={estilos.contador}>
            {futuros.length} {futuros.length === 1 ? 'bloqueio futuro' : 'bloqueios futuros'}
          </span>
        </div>

        {erro && (
          <div className={estilos.alertaErro} role="alert">
            {erro}
          </div>
        )}

        <div className={estilos.gradeCadastro}>
          <section className={estilos.painel}>
            <h2 className={estilos.tituloPainel}>
              {editandoId ? 'Editar bloqueio' : 'Novo bloqueio'}
            </h2>
            <form className={estilos.formulario} onSubmit={aoEnviar} noValidate>
              <div className={estilos.linhaDatas}>
                <label className={estilos.campo}>
                  <span className={estilos.rotulo}>Data inicial</span>
                  <input
                    type="date"
                    value={formulario.dataInicio}
                    onChange={(evento) => atualizarCampo('dataInicio', evento.target.value)}
                    aria-invalid={Boolean(errosCampos.dataInicio)}
                    className={`${estilos.entrada} ${
                      errosCampos.dataInicio ? estilos.entradaErro : ''
                    }`}
                  />
                  {errosCampos.dataInicio && (
                    <span className={estilos.mensagemErro}>{errosCampos.dataInicio}</span>
                  )}
                </label>

                <label className={estilos.campo}>
                  <span className={estilos.rotulo}>Data final</span>
                  <input
                    type="date"
                    min={formulario.dataInicio || undefined}
                    value={formulario.dataFim}
                    onChange={(evento) => atualizarCampo('dataFim', evento.target.value)}
                    aria-invalid={Boolean(errosCampos.dataFim)}
                    className={`${estilos.entrada} ${
                      errosCampos.dataFim ? estilos.entradaErro : ''
                    }`}
                  />
                  {errosCampos.dataFim && (
                    <span className={estilos.mensagemErro}>{errosCampos.dataFim}</span>
                  )}
                </label>
              </div>

              <label className={estilos.opcaoCheckbox}>
                <input
                  type="checkbox"
                  checked={formulario.diaInteiro}
                  onChange={(evento) => {
                    atualizarCampo('diaInteiro', evento.target.checked);
                    setErrosCampos((anteriores) => ({
                      ...anteriores,
                      horaInicio: undefined,
                      horaFim: undefined,
                    }));
                  }}
                />
                <span>Dia inteiro</span>
              </label>

              {!formulario.diaInteiro && (
                <div className={estilos.linhaHoras}>
                  <label className={estilos.campo}>
                    <span className={estilos.rotulo}>Hora de início</span>
                    <input
                      type="time"
                      value={formulario.horaInicio}
                      onChange={(evento) => atualizarCampo('horaInicio', evento.target.value)}
                      aria-invalid={Boolean(errosCampos.horaInicio)}
                      className={`${estilos.entrada} ${
                        errosCampos.horaInicio ? estilos.entradaErro : ''
                      }`}
                    />
                    {errosCampos.horaInicio && (
                      <span className={estilos.mensagemErro}>{errosCampos.horaInicio}</span>
                    )}
                  </label>

                  <label className={estilos.campo}>
                    <span className={estilos.rotulo}>Hora de fim</span>
                    <input
                      type="time"
                      value={formulario.horaFim}
                      onChange={(evento) => atualizarCampo('horaFim', evento.target.value)}
                      aria-invalid={Boolean(errosCampos.horaFim)}
                      className={`${estilos.entrada} ${
                        errosCampos.horaFim ? estilos.entradaErro : ''
                      }`}
                    />
                    {errosCampos.horaFim && (
                      <span className={estilos.mensagemErro}>{errosCampos.horaFim}</span>
                    )}
                  </label>
                </div>
              )}

              <label className={estilos.campo}>
                <span className={estilos.rotulo}>Motivo (opcional)</span>
                <input
                  type="text"
                  maxLength={TAMANHO_MAXIMO_MOTIVO_BLOQUEIO}
                  value={formulario.motivo}
                  onChange={(evento) => atualizarCampo('motivo', evento.target.value)}
                  placeholder="Ex.: Feriado, férias, congresso..."
                  aria-invalid={Boolean(errosCampos.motivo)}
                  className={`${estilos.entrada} ${
                    errosCampos.motivo ? estilos.entradaErro : ''
                  }`}
                />
                <div className={estilos.rodapeCampo}>
                  {errosCampos.motivo ? (
                    <span className={estilos.mensagemErro}>{errosCampos.motivo}</span>
                  ) : (
                    <span />
                  )}
                  <span className={estilos.contadorCaracteres}>
                    {formulario.motivo.trim().length}/{TAMANHO_MAXIMO_MOTIVO_BLOQUEIO}
                  </span>
                </div>
              </label>

              <div className={estilos.linhaBotoes}>
                <button type="submit" disabled={salvando} className={estilos.botaoPrimario}>
                  {salvando ? 'Salvando...' : editandoId ? 'Atualizar bloqueio' : 'Criar bloqueio'}
                </button>
                {editandoId && (
                  <button
                    type="button"
                    onClick={limparFormulario}
                    className={estilos.botaoSecundario}
                    disabled={salvando}
                  >
                    Cancelar
                  </button>
                )}
              </div>
            </form>
          </section>

          <section className={estilos.painel}>
            <div className={estilos.cabecalhoPainel}>
              <h2 className={estilos.tituloPainel}>Próximos bloqueios</h2>
            </div>

            {carregando ? (
              <p className={estilos.estadoVazio}>Carregando...</p>
            ) : gruposFuturos.length === 0 ? (
              <p className={estilos.estadoVazio}>Nenhum bloqueio futuro cadastrado.</p>
            ) : (
              <div className={estilos.listaCartoes}>
                {gruposFuturos.map((grupo) => {
                  if (grupo.bloqueios.length === 1) {
                    const bloqueio = grupo.bloqueios[0];
                    const diaInteiro = ehBloqueioDiaInteiro(bloqueio.inicio, bloqueio.fim);
                    return (
                      <article key={bloqueio.id} className={estilos.cartaoItem}>
                        <div className={estilos.cartaoCorpo}>
                          <div className={estilos.cartaoTopo}>
                            <div>
                              <strong className={estilos.nomeItem}>
                                {formatarPeriodoBloqueio(bloqueio.inicio, bloqueio.fim)}
                              </strong>
                              <span className={estilos.subItem}>
                                {bloqueio.motivo || 'Sem motivo informado'}
                              </span>
                            </div>
                            <span className={estilos.pilula}>
                              {diaInteiro ? 'Dia inteiro' : 'Faixa de horas'}
                            </span>
                          </div>
                          <div className={estilos.grupoAcoes}>
                            <button
                              type="button"
                              className={estilos.botaoAcaoPositivo}
                              disabled={salvando}
                              onClick={() => iniciarEdicao(bloqueio)}
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              className={estilos.botaoAcaoPerigo}
                              disabled={salvando}
                              onClick={() => aoRemover(bloqueio)}
                            >
                              Remover
                            </button>
                          </div>
                        </div>
                      </article>
                    );
                  }

                  return (
                    <article
                      key={`${grupo.inicio}-${grupo.fim}`}
                      className={estilos.cartaoItem}
                    >
                      <div className={estilos.cartaoCorpo}>
                        <div className={estilos.cartaoTopo}>
                          <div>
                            <strong className={estilos.nomeItem}>
                              {formatarPeriodoBloqueio(grupo.inicio, grupo.fim)}
                            </strong>
                            <span className={estilos.subItem}>
                              Bloqueios sobrepostos exibidos em um único período contínuo
                            </span>
                          </div>
                          <span className={estilos.badgeAviso}>
                            Período unido · {grupo.bloqueios.length} bloqueios
                          </span>
                        </div>
                        <div className={estilos.sublistaSobrepostos}>
                          {grupo.bloqueios.map((bloqueio) => (
                            <div key={bloqueio.id} className={estilos.itemSobreposto}>
                              <div>
                                <div className={estilos.celulaPrincipal}>
                                  {formatarPeriodoBloqueio(bloqueio.inicio, bloqueio.fim)}
                                </div>
                                <div className={estilos.celulaSecundaria}>
                                  {bloqueio.motivo || 'Sem motivo informado'}
                                </div>
                              </div>
                              <div className={estilos.grupoAcoes}>
                                <button
                                  type="button"
                                  className={estilos.botaoAcaoPositivo}
                                  disabled={salvando}
                                  onClick={() => iniciarEdicao(bloqueio)}
                                >
                                  Editar
                                </button>
                                <button
                                  type="button"
                                  className={estilos.botaoAcaoPerigo}
                                  disabled={salvando}
                                  onClick={() => aoRemover(bloqueio)}
                                >
                                  Remover
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}

            <div className={estilos.secaoHistorico}>
              <button
                type="button"
                className={estilos.botaoHistorico}
                aria-expanded={historicoAberto}
                onClick={() => setHistoricoAberto((atual) => !atual)}
              >
                <span>Histórico ({historico.length})</span>
                <span className={estilos.celulaSecundaria}>
                  {historicoAberto ? 'Ocultar' : 'Mostrar'}
                </span>
              </button>

              {historicoAberto && (
                <div className={estilos.conteudoHistorico}>
                  {historico.length === 0 ? (
                    <p className={estilos.estadoVazio}>Nenhum bloqueio passado registrado.</p>
                  ) : (
                    <div className={estilos.listaCartoes}>
                      {historico.map((bloqueio) => (
                        <article key={bloqueio.id} className={estilos.cartaoItem}>
                          <div className={estilos.cartaoCorpo}>
                            <div className={estilos.cartaoTopo}>
                              <div>
                                <strong className={estilos.nomeItem}>
                                  {formatarPeriodoBloqueio(bloqueio.inicio, bloqueio.fim)}
                                </strong>
                                <span className={estilos.subItem}>
                                  {bloqueio.motivo || 'Sem motivo informado'}
                                </span>
                              </div>
                              <span className={estilos.pilula}>Encerrado</span>
                            </div>
                            <div className={estilos.grupoAcoes}>
                              <button
                                type="button"
                                className={estilos.botaoAcaoPositivo}
                                disabled={salvando}
                                onClick={() => iniciarEdicao(bloqueio)}
                              >
                                Editar
                              </button>
                              <button
                                type="button"
                                className={estilos.botaoAcaoPerigo}
                                disabled={salvando}
                                onClick={() => aoRemover(bloqueio)}
                              >
                                Remover
                              </button>
                            </div>
                          </div>
                        </article>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>
      </main>

      {conflitoPendente && (
        <div
          className={estilos.modalFundo}
          onClick={() => {
            if (!salvando) {
              setConflitoPendente(null);
            }
          }}
        >
          <div
            className={estilos.modalCartao}
            role="dialog"
            aria-modal="true"
            aria-labelledby="titulo-conflito-bloqueio"
            onClick={(evento) => evento.stopPropagation()}
          >
            <h2 id="titulo-conflito-bloqueio" className={estilos.tituloPainel}>
              Agendamentos encontrados neste período
            </h2>
            <p className={estilos.descricao}>
              {conflitoPendente.mensagem} Nenhum agendamento será cancelado automaticamente — caso
              prossiga, você precisará tratá-los manualmente na Agenda.
            </p>

            <ul className={estilos.modalListaConflitos}>
              {conflitoPendente.agendamentos.map((agendamento) => (
                <li key={agendamento.id} className={estilos.modalItemConflito}>
                  <div>
                    <div className={estilos.celulaPrincipal}>{agendamento.nomeCliente}</div>
                    <div className={estilos.celulaSecundaria}>
                      {agendamento.servicoNome} · {formatarDataHoraLocal(agendamento.inicio)}
                    </div>
                  </div>
                  <BadgeStatus status={agendamento.status} />
                </li>
              ))}
            </ul>

            <div className={estilos.modalAcoes}>
              <button
                type="button"
                className={estilos.botaoSecundario}
                disabled={salvando}
                onClick={() => setConflitoPendente(null)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className={estilos.botaoPrimario}
                disabled={salvando}
                onClick={confirmarBloqueioComConflito}
              >
                {salvando ? 'Bloqueando...' : 'Bloquear mesmo assim'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
