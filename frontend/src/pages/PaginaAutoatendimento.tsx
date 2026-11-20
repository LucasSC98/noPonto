import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { BadgeStatus } from '../components/BadgeStatus';
import {
  buscarSlotsAutoatendimento,
  cancelarAgendamentoPorToken,
  consultarAgendamentoPorToken,
  remarcarAgendamentoPorToken,
} from '../services/agendamentoService';
import { extrairMensagemErro } from '../services/api';
import type { AgendamentoAutoatendimentoResponse } from '../types/dominio';
import {
  dataHojeIsoLocal,
  dataNoPassado,
  formatarDataExtenso,
  formatarDataHoraLocal,
  formatarHoraSlot,
} from '../utils/datas';
import estilos from './PaginaAgendamentoPublico.module.css';

export function PaginaAutoatendimento() {
  const { token } = useParams<{ token: string }>();
  const hojeIso = dataHojeIsoLocal();

  const [agendamento, setAgendamento] =
    useState<AgendamentoAutoatendimentoResponse | null>(null);
  const [carregando, setCarregando] = useState(Boolean(token));
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);
  const [erroAcao, setErroAcao] = useState<string | null>(null);
  const [mensagemSucesso, setMensagemSucesso] = useState<string | null>(null);
  const [processando, setProcessando] = useState(false);

  const [modoRemarcacao, setModoRemarcacao] = useState(false);
  const [dataNova, setDataNova] = useState('');
  const [slotNovo, setSlotNovo] = useState('');
  const [slotsDisponiveis, setSlotsDisponiveis] = useState<string[]>([]);
  const [carregandoSlots, setCarregandoSlots] = useState(false);
  const [erroSlots, setErroSlots] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      return;
    }

    let cancelado = false;

    async function carregarAgendamento() {
      try {
        const dados = await consultarAgendamentoPorToken(token!);
        if (!cancelado) {
          setAgendamento(dados);
        }
      } catch (falha) {
        if (!cancelado) {
          setErroCarregamento(
            extrairMensagemErro(
              falha,
              'Agendamento não encontrado ou link de autoatendimento expirado.',
            ),
          );
        }
      } finally {
        if (!cancelado) {
          setCarregando(false);
        }
      }
    }

    carregarAgendamento();

    return () => {
      cancelado = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token || !modoRemarcacao || !dataNova || dataNoPassado(dataNova)) {
      return;
    }

    let cancelado = false;

    async function carregarSlots() {
      setCarregandoSlots(true);
      setErroSlots(null);
      setSlotNovo('');
      try {
        const lista = await buscarSlotsAutoatendimento(token!, dataNova);
        if (!cancelado) {
          setSlotsDisponiveis(lista ?? []);
        }
      } catch (falha) {
        if (!cancelado) {
          setSlotsDisponiveis([]);
          setErroSlots(
            extrairMensagemErro(falha, 'Não foi possível carregar os horários disponíveis.'),
          );
        }
      } finally {
        if (!cancelado) {
          setCarregandoSlots(false);
        }
      }
    }

    carregarSlots();

    return () => {
      cancelado = true;
    };
  }, [token, modoRemarcacao, dataNova]);

  async function aoCancelar() {
    if (!token || !agendamento) {
      return;
    }

    const confirmado = window.confirm(
      `Confirmar o cancelamento do seu agendamento de ${agendamento.servicoNome} em ${formatarDataHoraLocal(agendamento.inicio)}?`,
    );
    if (!confirmado) {
      return;
    }

    setProcessando(true);
    setErroAcao(null);
    setMensagemSucesso(null);
    try {
      const atualizado = await cancelarAgendamentoPorToken(token);
      setAgendamento(atualizado);
      setModoRemarcacao(false);
      setMensagemSucesso('Seu agendamento foi cancelado com sucesso.');
    } catch (falha) {
      setErroAcao(extrairMensagemErro(falha, 'Não foi possível cancelar o agendamento.'));
    } finally {
      setProcessando(false);
    }
  }

  async function aoConfirmarRemarcacao(evento: FormEvent) {
    evento.preventDefault();
    if (!token || !agendamento) {
      return;
    }

    if (!dataNova || dataNoPassado(dataNova)) {
      setErroAcao('Selecione uma data igual ou posterior a hoje.');
      return;
    }
    if (!slotNovo) {
      setErroAcao('Selecione um novo horário disponível.');
      return;
    }

    setProcessando(true);
    setErroAcao(null);
    setMensagemSucesso(null);
    try {
      const atualizado = await remarcarAgendamentoPorToken(token, slotNovo);
      setAgendamento(atualizado);
      setModoRemarcacao(false);
      setDataNova('');
      setSlotNovo('');
      setSlotsDisponiveis([]);
      setMensagemSucesso('Horário remarcado com sucesso!');
    } catch (falha) {
      setErroAcao(
        extrairMensagemErro(
          falha,
          'Não foi possível remarcar o agendamento. Tente outro horário.',
        ),
      );
    } finally {
      setProcessando(false);
    }
  }

  if (!token) {
    return (
      <main className={estilos.container}>
        <section className={estilos.card}>
          <h1 className={estilos.titulo}>Link inválido</h1>
          <p className={estilos.subtitulo}>Token de autoatendimento não informado.</p>
        </section>
      </main>
    );
  }

  if (carregando) {
    return (
      <main className={estilos.container}>
        <section className={estilos.card}>
          <span className={estilos.marca}>NoPonto</span>
          <h1 className={estilos.titulo}>Consultando agendamento...</h1>
          <p className={estilos.subtitulo}>Aguarde um instante.</p>
        </section>
      </main>
    );
  }

  if (erroCarregamento || !agendamento) {
    return (
      <main className={estilos.container}>
        <section className={estilos.card}>
          <span className={estilos.marca}>NoPonto</span>
          <h1 className={estilos.titulo}>Link indisponível</h1>
          <div className={estilos.alertaErro} role="alert" style={{ marginTop: '1rem' }}>
            {erroCarregamento ?? 'Agendamento não encontrado ou link expirado.'}
          </div>
        </section>
      </main>
    );
  }

  const podeGerenciar =
    agendamento.status === 'PENDENTE' || agendamento.status === 'CONFIRMADO';

  return (
    <main className={estilos.container}>
      <section className={estilos.card}>
        <header className={estilos.cabecalho}>
          <span className={estilos.marca}>{agendamento.nomeEstabelecimento}</span>
          <h1 className={estilos.titulo}>Gerenciar agendamento</h1>
          <p className={estilos.subtitulo}>
            Consulte os dados da sua reserva ou solicite remarcação e cancelamento com até{' '}
            {agendamento.antecedenciaMinimaCancelamentoMinutos} minutos de antecedência.
          </p>
        </header>

        {mensagemSucesso && (
          <div className={estilos.sucesso} role="status" style={{ marginBottom: '1.25rem' }}>
            <h2 className={estilos.tituloSucesso}>{mensagemSucesso}</h2>
          </div>
        )}

        {erroAcao && (
          <div className={estilos.alertaErro} role="alert" style={{ marginBottom: '1.25rem' }}>
            {erroAcao}
          </div>
        )}

        <dl className={estilos.resumoLista}>
          <div className={estilos.resumoItem}>
            <dt>Cliente</dt>
            <dd>{agendamento.nomeCliente}</dd>
          </div>
          {agendamento.emailCliente && (
            <div className={estilos.resumoItem}>
              <dt>E-mail</dt>
              <dd>{agendamento.emailCliente}</dd>
            </div>
          )}
          <div className={estilos.resumoItem}>
            <dt>Profissional</dt>
            <dd>
              {agendamento.profissionalEspecialidade
                ? `${agendamento.profissionalNome} · ${agendamento.profissionalEspecialidade}`
                : agendamento.profissionalNome}
            </dd>
          </div>
          <div className={estilos.resumoItem}>
            <dt>Serviço</dt>
            <dd>
              {agendamento.servicoNome} ({agendamento.servicoDuracaoMinutos} min)
            </dd>
          </div>
          <div className={estilos.resumoItem}>
            <dt>Data e horário</dt>
            <dd>{formatarDataHoraLocal(agendamento.inicio)}</dd>
          </div>
          <div className={estilos.resumoItem}>
            <dt>Status</dt>
            <dd style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <BadgeStatus status={agendamento.status} />
              {agendamento.remarcado && <span>(Remarcado)</span>}
            </dd>
          </div>
        </dl>

        {podeGerenciar && (
          <div
            style={{
              display: 'flex',
              gap: '0.75rem',
              flexWrap: 'wrap',
              marginTop: '1.25rem',
            }}
          >
            <button
              type="button"
              className={estilos.botaoSecundario}
              disabled={processando}
              onClick={() => {
                setErroAcao(null);
                setMensagemSucesso(null);
                setModoRemarcacao((atual) => !atual);
              }}
            >
              {modoRemarcacao ? 'Fechar remarcação' : 'Remarcar'}
            </button>
            <button
              type="button"
              className={estilos.botaoSecundario}
              disabled={processando}
              onClick={aoCancelar}
            >
              {processando && !modoRemarcacao ? 'Cancelando...' : 'Cancelar'}
            </button>
          </div>
        )}

        {podeGerenciar && modoRemarcacao && (
          <form
            className={estilos.formulario}
            style={{ marginTop: '1.5rem' }}
            onSubmit={aoConfirmarRemarcacao}
            noValidate
          >
            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>1. Escolha a nova data</legend>
              <div className={estilos.campo}>
                <label htmlFor="novaData" className={estilos.rotulo}>
                  Data do atendimento
                </label>
                <input
                  id="novaData"
                  type="date"
                  min={hojeIso}
                  value={dataNova}
                  onChange={(evento) => setDataNova(evento.target.value)}
                  className={estilos.entrada}
                />
              </div>
            </fieldset>

            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>2. Escolha o novo horário</legend>
              {!dataNova ? (
                <p className={estilos.textoAuxiliar}>
                  Selecione uma data acima para consultar os horários disponíveis.
                </p>
              ) : dataNoPassado(dataNova) ? (
                <p className={estilos.mensagemErro}>
                  Selecione uma data igual ou posterior a hoje.
                </p>
              ) : carregandoSlots ? (
                <p className={estilos.textoAuxiliar}>Buscando horários disponíveis...</p>
              ) : erroSlots ? (
                <div className={estilos.alertaErro} role="alert">
                  {erroSlots}
                </div>
              ) : slotsDisponiveis.length === 0 ? (
                <p className={estilos.textoAuxiliar}>
                  Nenhum horário disponível para esta data. Experimente outro dia.
                </p>
              ) : (
                <div
                  className={estilos.gradeSlots}
                  role="radiogroup"
                  aria-label="Novos horários disponíveis"
                >
                  {slotsDisponiveis.map((slotIso) => {
                    const selecionado = slotNovo === slotIso;
                    return (
                      <button
                        key={slotIso}
                        type="button"
                        role="radio"
                        aria-checked={selecionado}
                        className={`${estilos.botaoSlot} ${
                          selecionado ? estilos.botaoSlotSelecionado : ''
                        }`}
                        onClick={() => setSlotNovo(slotIso)}
                      >
                        {formatarHoraSlot(slotIso)}
                      </button>
                    );
                  })}
                </div>
              )}

              {dataNova && slotNovo && (
                <div className={estilos.resumoBox}>
                  <p className={estilos.resumoLinha}>
                    <strong>Nova data:</strong> {formatarDataExtenso(dataNova)}
                  </p>
                  <p className={estilos.resumoLinha}>
                    <strong>Novo horário:</strong> {formatarHoraSlot(slotNovo)}
                  </p>
                </div>
              )}

              <button
                type="submit"
                className={estilos.botaoPrimario}
                disabled={processando || !slotNovo}
              >
                {processando ? 'Remarcando...' : 'Confirmar remarcação'}
              </button>
            </fieldset>
          </form>
        )}
      </section>
    </main>
  );
}
