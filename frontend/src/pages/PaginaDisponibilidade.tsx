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
import { BarraPainel } from '../components/BarraPainel';
import type { DiaSemana, RegraDisponibilidadeResponse } from '../types/gestao';
import estilos from './PaginaDashboard.module.css';

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
      <BarraPainel atual="/profissionais" />

      <main className={estilos.conteudo}>
        <div className={estilos.introducao}>
          <div>
            <h1 className={estilos.tituloGrande}>Horários de {nomeProfissional}</h1>
            <p className={estilos.descricao}>
              Cada dia pode ter várias faixas. O fim precisa ser depois do início, e faixas do mesmo dia não se sobrepõem.
            </p>
          </div>
        </div>

        {erro && <div className={estilos.alertaErro}>{erro}</div>}

        {carregando ? (
          <p className={estilos.estadoVazio}>Carregando...</p>
        ) : (
          <div className={estilos.semana}>
            {DIAS.map((dia) => {
              const faixas = regras.filter((regra) => regra.diaSemana === dia.valor);
              return (
                <section key={dia.valor} className={estilos.diaCard}>
                  <h2 className={estilos.diaNome}>{dia.rotulo.replace('-feira', '')}</h2>
                  <div className={estilos.faixas}>
                    {faixas.length === 0 && <p className={estilos.subItem}>Sem atendimento</p>}
                    {faixas.map((faixa) =>
                      editandoId === faixa.id ? (
                        <form key={faixa.id} className={estilos.formDia} onSubmit={(evento) => aoSalvarEdicao(evento, faixa)}>
                          <input
                            type="time"
                            required
                            value={inicioEdicao}
                            aria-label="Início"
                            onChange={(evento) => setInicioEdicao(evento.target.value)}
                            className={estilos.entrada}
                          />
                          <input
                            type="time"
                            required
                            value={fimEdicao}
                            aria-label="Fim"
                            onChange={(evento) => setFimEdicao(evento.target.value)}
                            className={estilos.entrada}
                          />
                          <div className={estilos.faixaAcoes}>
                            <button type="submit" className={estilos.botaoAcaoPositivo} disabled={salvando}>
                              Salvar
                            </button>
                            <button type="button" className={estilos.botaoSecundario} onClick={() => setEditandoId(null)}>
                              Cancelar
                            </button>
                          </div>
                        </form>
                      ) : (
                        <div key={faixa.id} className={estilos.faixa}>
                          <span>
                            {offsetTimeParaHora(faixa.horaInicio)} – {offsetTimeParaHora(faixa.horaFim)}
                          </span>
                          <div className={estilos.faixaAcoes}>
                            <button type="button" className={estilos.botaoAcaoPositivo} onClick={() => iniciarEdicao(faixa)}>
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
                        </div>
                      ),
                    )}
                  </div>
                  <form className={estilos.formDia} onSubmit={(evento) => aoAdicionar(evento, dia.valor)}>
                    <input type="time" name="inicio" required defaultValue="08:00" aria-label={`Início em ${dia.rotulo}`} className={estilos.entrada} />
                    <input type="time" name="fim" required defaultValue="12:00" aria-label={`Fim em ${dia.rotulo}`} className={estilos.entrada} />
                    <button type="submit" className={estilos.botaoPrimario} disabled={salvando}>
                      Adicionar
                    </button>
                  </form>
                </section>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}
