import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import {
  atualizarRegra,
  criarRegra,
  listarProfissionais,
  listarRegras,
  removerRegra,
} from '../services/gestaoService';
import { extrairMensagemErro } from '../services/api';
import { removerToken } from '../services/authService';
import type { DiaSemana, RegraDisponibilidadeResponse } from '../types/gestao';
import estilos from './PaginaDashboard.module.css';
import estilosFormulario from './PaginaAgendamentoPublico.module.css';

const DIAS: { valor: DiaSemana; rotulo: string }[] = [
  { valor: 'MONDAY', rotulo: 'Segunda-feira' },
  { valor: 'TUESDAY', rotulo: 'Terça-feira' },
  { valor: 'WEDNESDAY', rotulo: 'Quarta-feira' },
  { valor: 'THURSDAY', rotulo: 'Quinta-feira' },
  { valor: 'FRIDAY', rotulo: 'Sexta-feira' },
  { valor: 'SATURDAY', rotulo: 'Sábado' },
  { valor: 'SUNDAY', rotulo: 'Domingo' },
];

const OFFSET_ESTABELECIMENTO = '-03:00';

function horaParaOffsetTime(hora: string): string {
  const [horas, minutos] = hora.split(':');
  return `${horas.padStart(2, '0')}:${(minutos ?? '00').padStart(2, '0')}:00${OFFSET_ESTABELECIMENTO}`;
}

function offsetTimeParaHora(valor: string): string {
  return valor.slice(0, 5);
}

export function PaginaDisponibilidade() {
  const { profissionalId } = useParams<{ profissionalId: string }>();
  const navegar = useNavigate();

  const [nomeProfissional, setNomeProfissional] = useState('Profissional');
  const [regras, setRegras] = useState<RegraDisponibilidadeResponse[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [inicioEdicao, setInicioEdicao] = useState('08:00');
  const [fimEdicao, setFimEdicao] = useState('12:00');
  const [salvando, setSalvando] = useState(false);

  const carregar = useCallback(async () => {
    if (!profissionalId) {
      setErro('Profissional não informado.');
      setCarregando(false);
      return;
    }

    setCarregando(true);
    setErro(null);
    try {
      const [lista, pagina] = await Promise.all([
        listarRegras(profissionalId),
        listarProfissionais(0, 100),
      ]);
      setRegras(lista);
      const profissional = pagina.content.find((item) => item.id === profissionalId);
      if (profissional) {
        setNomeProfissional(profissional.nome);
      }
    } catch (falha) {
      if (axios.isAxiosError(falha) && falha.response?.status === 401) {
        removerToken();
        navegar('/login', { replace: true });
        return;
      }
      setErro(extrairMensagemErro(falha, 'Não foi possível carregar a disponibilidade.'));
    } finally {
      setCarregando(false);
    }
  }, [profissionalId, navegar]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function iniciarEdicao(regra: RegraDisponibilidadeResponse) {
    setEditandoId(regra.id);
    setInicioEdicao(offsetTimeParaHora(regra.horaInicio));
    setFimEdicao(offsetTimeParaHora(regra.horaFim));
    setErro(null);
  }

  async function aoAdicionar(evento: FormEvent<HTMLFormElement>, dia: DiaSemana) {
    evento.preventDefault();
    if (!profissionalId) {
      return;
    }

    const formularioEl = evento.currentTarget;
    const formulario = new FormData(formularioEl);
    const inicio = String(formulario.get('inicio') ?? '');
    const fim = String(formulario.get('fim') ?? '');

    setSalvando(true);
    setErro(null);
    try {
      await criarRegra(profissionalId, {
        diaSemana: dia,
        horaInicio: horaParaOffsetTime(inicio),
        horaFim: horaParaOffsetTime(fim),
      });
      formularioEl.reset();
      await carregar();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível adicionar a faixa de horário.'));
    } finally {
      setSalvando(false);
    }
  }

  async function aoSalvarEdicao(evento: FormEvent, regra: RegraDisponibilidadeResponse) {
    evento.preventDefault();
    if (!profissionalId) {
      return;
    }

    setSalvando(true);
    setErro(null);
    try {
      await atualizarRegra(profissionalId, regra.id, {
        diaSemana: regra.diaSemana,
        horaInicio: horaParaOffsetTime(inicioEdicao),
        horaFim: horaParaOffsetTime(fimEdicao),
      });
      setEditandoId(null);
      await carregar();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível atualizar a faixa de horário.'));
    } finally {
      setSalvando(false);
    }
  }

  async function aoRemover(regraId: string) {
    if (!profissionalId) {
      return;
    }

    setSalvando(true);
    setErro(null);
    try {
      await removerRegra(profissionalId, regraId);
      if (editandoId === regraId) {
        setEditandoId(null);
      }
      await carregar();
    } catch (falha) {
      setErro(extrairMensagemErro(falha, 'Não foi possível remover a faixa de horário.'));
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
            <span className={estilos.tituloPagina}>Disponibilidade</span>
          </div>
          <div className={estilos.acoesBarra}>
            <button type="button" className={estilos.botaoSecundario} onClick={() => navegar('/profissionais')}>
              Voltar aos profissionais
            </button>
          </div>
        </div>
      </header>

      <main className={estilos.conteudo}>
        <section className={estilos.painel}>
          <h1 className={estilos.tituloPainel}>Horários de {nomeProfissional}</h1>
          <p className={estilosFormulario.textoAuxiliar}>
            Cadastre as faixas em que este profissional atende. O fim precisa ser depois do início,
            e as faixas do mesmo dia não podem se sobrepor.
          </p>
          {erro && <div className={estilos.alertaErro}>{erro}</div>}
        </section>

        {carregando ? (
          <p className={estilos.estadoVazio}>Carregando...</p>
        ) : (
          DIAS.map((dia) => {
            const faixas = regras.filter((regra) => regra.diaSemana === dia.valor);
            return (
              <section key={dia.valor} className={estilos.painel}>
                <h2 className={estilos.tituloPainel}>{dia.rotulo}</h2>

                {faixas.length === 0 ? (
                  <p className={estilos.estadoVazio}>Nenhuma faixa neste dia.</p>
                ) : (
                  <div className={estilos.tabelaWrapper}>
                    <table className={estilos.tabela}>
                      <thead>
                        <tr>
                          <th>Início</th>
                          <th>Fim</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {faixas.map((faixa) => (
                          <tr key={faixa.id}>
                            {editandoId === faixa.id ? (
                              <td colSpan={3}>
                                <form
                                  onSubmit={(evento) => aoSalvarEdicao(evento, faixa)}
                                  className={estilosFormulario.gradeCampos}
                                >
                                  <label className={estilosFormulario.campo}>
                                    <span className={estilosFormulario.rotulo}>Início</span>
                                    <input
                                      type="time"
                                      required
                                      value={inicioEdicao}
                                      onChange={(evento) => setInicioEdicao(evento.target.value)}
                                      className={estilosFormulario.entrada}
                                    />
                                  </label>
                                  <label className={estilosFormulario.campo}>
                                    <span className={estilosFormulario.rotulo}>Fim</span>
                                    <input
                                      type="time"
                                      required
                                      value={fimEdicao}
                                      onChange={(evento) => setFimEdicao(evento.target.value)}
                                      className={estilosFormulario.entrada}
                                    />
                                  </label>
                                  <div className={estilos.grupoAcoes}>
                                    <button type="submit" className={estilos.botaoAcaoPositivo} disabled={salvando}>
                                      Salvar
                                    </button>
                                    <button
                                      type="button"
                                      className={estilos.botaoSecundario}
                                      onClick={() => setEditandoId(null)}
                                    >
                                      Cancelar
                                    </button>
                                  </div>
                                </form>
                              </td>
                            ) : (
                              <>
                                <td>{offsetTimeParaHora(faixa.horaInicio)}</td>
                                <td>{offsetTimeParaHora(faixa.horaFim)}</td>
                                <td>
                                  <div className={estilos.grupoAcoes}>
                                    <button
                                      type="button"
                                      className={estilos.botaoAcaoPositivo}
                                      onClick={() => iniciarEdicao(faixa)}
                                    >
                                      Editar
                                    </button>
                                    <button
                                      type="button"
                                      className={estilos.botaoAcaoPerigo}
                                      disabled={salvando}
                                      onClick={() => aoRemover(faixa.id)}
                                    >
                                      Remover
                                    </button>
                                  </div>
                                </td>
                              </>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                <form onSubmit={(evento) => aoAdicionar(evento, dia.valor)} className={estilosFormulario.gradeCampos}>
                  <label className={estilosFormulario.campo}>
                    <span className={estilosFormulario.rotulo}>Início</span>
                    <input type="time" name="inicio" required defaultValue="08:00" className={estilosFormulario.entrada} />
                  </label>
                  <label className={estilosFormulario.campo}>
                    <span className={estilosFormulario.rotulo}>Fim</span>
                    <input type="time" name="fim" required defaultValue="12:00" className={estilosFormulario.entrada} />
                  </label>
                  <div className={estilos.grupoAcoes}>
                    <button type="submit" className={estilos.botaoSecundario} disabled={salvando}>
                      Adicionar faixa
                    </button>
                  </div>
                </form>
              </section>
            );
          })
        )}
      </main>
    </div>
  );
}
