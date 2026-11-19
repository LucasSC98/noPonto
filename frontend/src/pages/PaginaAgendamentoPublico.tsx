import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useParams, useSearchParams } from 'react-router-dom';
import { buscarSlotsDisponiveis, criarAgendamentoPublico } from '../services/agendamentoService';
import { extrairMensagemErro } from '../services/api';
import {
  dataHojeIsoLocal,
  dataNoPassado,
  formatarDataExtenso,
  formatarDataHoraLocal,
  formatarHoraSlot,
} from '../utils/datas';
import { esquemaAgendamentoPublico } from '../utils/validacao';
import type { DadosAgendamentoPublico } from '../utils/validacao';
import type { AgendamentoResponse } from '../types/dominio';
import estilos from './PaginaAgendamentoPublico.module.css';

const PASSOS = [
  { numero: 1, rotulo: 'Data' },
  { numero: 2, rotulo: 'Horário' },
  { numero: 3, rotulo: 'Seus dados' },
  { numero: 4, rotulo: 'Confirmação' },
] as const;

export function PaginaAgendamentoPublico() {
  const { profissionalId } = useParams<{ profissionalId: string }>();
  const [searchParams] = useSearchParams();

  const servicoInicial = searchParams.get('servicoId') ?? '';
  const subdominioTenant = searchParams.get('tenant') ?? undefined;
  const hojeIso = dataHojeIsoLocal();

  const [slots, setSlots] = useState<string[]>([]);
  const [carregandoSlots, setCarregandoSlots] = useState(false);
  const [erroSlots, setErroSlots] = useState<string | null>(null);
  const [erroEnvio, setErroEnvio] = useState<string | null>(null);
  const [agendamentoConfirmado, setAgendamentoConfirmado] = useState<AgendamentoResponse | null>(
    null,
  );

  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<DadosAgendamentoPublico>({
    resolver: zodResolver(esquemaAgendamentoPublico),
    defaultValues: {
      data: '',
      servicoItemId: servicoInicial,
      slot: '',
      nomeCliente: '',
      emailCliente: '',
    },
  });

  const valores = useWatch({ control });
  const dataSelecionada = valores.data ?? '';
  const servicoSelecionado = valores.servicoItemId ?? '';
  const slotSelecionado = valores.slot ?? '';
  const nomeCliente = valores.nomeCliente ?? '';
  const emailCliente = valores.emailCliente ?? '';

  useEffect(() => {
    if (!profissionalId || !dataSelecionada || dataNoPassado(dataSelecionada)) {
      return;
    }

    let cancelado = false;

    async function carregarSlots() {
      setCarregandoSlots(true);
      setErroSlots(null);
      setValue('slot', '', { shouldValidate: false });
      try {
        const disponiveis = await buscarSlotsDisponiveis(
          profissionalId!,
          dataSelecionada,
          servicoSelecionado || undefined,
          subdominioTenant,
        );
        if (!cancelado) {
          setSlots(disponiveis);
        }
      } catch (erro) {
        if (!cancelado) {
          setSlots([]);
          setErroSlots(
            extrairMensagemErro(erro, 'Não foi possível carregar os horários disponíveis.'),
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
  }, [profissionalId, dataSelecionada, servicoSelecionado, subdominioTenant, setValue]);

  async function aoConfirmar(dados: DadosAgendamentoPublico) {
    if (!profissionalId) {
      setErroEnvio('Profissional não informado na URL.');
      return;
    }

    setErroEnvio(null);
    try {
      const criado = await criarAgendamentoPublico(
        {
          profissionalId,
          servicoItemId: dados.servicoItemId,
          nomeCliente: dados.nomeCliente,
          emailCliente: dados.emailCliente,
          inicio: dados.slot,
        },
        subdominioTenant,
      );
      setAgendamentoConfirmado(criado);
    } catch (erro) {
      setErroEnvio(
        extrairMensagemErro(erro, 'Não foi possível concluir o agendamento. Tente outro horário.'),
      );
    }
  }

  function reiniciarFluxo() {
    setAgendamentoConfirmado(null);
    setSlots([]);
    setErroSlots(null);
    setErroEnvio(null);
    reset({
      data: '',
      servicoItemId: servicoInicial,
      slot: '',
      nomeCliente: '',
      emailCliente: '',
    });
  }

  const passoAtual = agendamentoConfirmado
    ? 4
    : slotSelecionado && nomeCliente.trim() && emailCliente.trim()
      ? 4
      : slotSelecionado
        ? 3
        : dataSelecionada && !dataNoPassado(dataSelecionada)
          ? 2
          : 1;

  if (!profissionalId) {
    return (
      <main className={estilos.container}>
        <section className={estilos.card}>
          <h1 className={estilos.titulo}>Link de agendamento inválido</h1>
          <p className={estilos.subtitulo}>
            Informe um identificador de profissional válido na URL para continuar.
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className={estilos.container}>
      <section className={estilos.card}>
        <header className={estilos.cabecalho}>
          <span className={estilos.marca}>NoPonto</span>
          <h1 className={estilos.titulo}>Agendar horário</h1>
          <p className={estilos.subtitulo}>
            Escolha a data, selecione um horário disponível e confirme seus dados.
          </p>
        </header>

        <ol className={estilos.passos} aria-label="Etapas do agendamento">
          {PASSOS.map((passo) => {
            const ativo = passo.numero <= passoAtual;
            return (
              <li
                key={passo.numero}
                className={`${estilos.passo} ${ativo ? estilos.passoAtivo : ''}`}
              >
                <span className={estilos.numeroPasso}>{passo.numero}</span>
                <span>{passo.rotulo}</span>
              </li>
            );
          })}
        </ol>

        {agendamentoConfirmado ? (
          <div className={estilos.sucesso} role="status">
            <h2 className={estilos.tituloSucesso}>Agendamento confirmado!</h2>
            <p className={estilos.textoSucesso}>
              Seu horário foi reservado com sucesso. Confira os detalhes abaixo:
            </p>
            <dl className={estilos.resumoLista}>
              <div className={estilos.resumoItem}>
                <dt>Cliente</dt>
                <dd>{agendamentoConfirmado.nomeCliente}</dd>
              </div>
              {agendamentoConfirmado.emailCliente && (
                <div className={estilos.resumoItem}>
                  <dt>E-mail</dt>
                  <dd>{agendamentoConfirmado.emailCliente}</dd>
                </div>
              )}
              <div className={estilos.resumoItem}>
                <dt>Data e horário</dt>
                <dd>{formatarDataHoraLocal(agendamentoConfirmado.inicio)}</dd>
              </div>
              <div className={estilos.resumoItem}>
                <dt>Status</dt>
                <dd>{agendamentoConfirmado.status}</dd>
              </div>
            </dl>
            <button type="button" className={estilos.botaoSecundario} onClick={reiniciarFluxo}>
              Fazer novo agendamento
            </button>
          </div>
        ) : (
          <form className={estilos.formulario} onSubmit={handleSubmit(aoConfirmar)} noValidate>
            {/* Passo 1: Seleção da data (e serviço, caso não informado na URL) */}
            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>1. Selecione a data</legend>

              {!servicoInicial && (
                <div className={estilos.campo}>
                  <label htmlFor="servicoItemId" className={estilos.rotulo}>
                    ID do serviço
                  </label>
                  <input
                    id="servicoItemId"
                    type="text"
                    placeholder="UUID do serviço"
                    className={`${estilos.entrada} ${errors.servicoItemId ? estilos.entradaErro : ''}`}
                    {...register('servicoItemId')}
                  />
                  {errors.servicoItemId && (
                    <span className={estilos.mensagemErro}>{errors.servicoItemId.message}</span>
                  )}
                </div>
              )}

              <div className={estilos.campo}>
                <label htmlFor="data" className={estilos.rotulo}>
                  Data do atendimento
                </label>
                <input
                  id="data"
                  type="date"
                  min={hojeIso}
                  className={`${estilos.entrada} ${errors.data ? estilos.entradaErro : ''}`}
                  {...register('data')}
                />
                {errors.data && <span className={estilos.mensagemErro}>{errors.data.message}</span>}
              </div>
            </fieldset>

            {/* Passo 2: Horários disponíveis */}
            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>2. Escolha um horário</legend>
              <input type="hidden" {...register('slot')} />

              {!dataSelecionada ? (
                <p className={estilos.textoAuxiliar}>
                  Selecione uma data acima para consultar os horários disponíveis.
                </p>
              ) : dataNoPassado(dataSelecionada) ? (
                <p className={estilos.mensagemErro}>
                  Selecione uma data igual ou posterior a hoje.
                </p>
              ) : carregandoSlots ? (
                <p className={estilos.textoAuxiliar}>Buscando horários disponíveis...</p>
              ) : erroSlots ? (
                <div className={estilos.alertaErro} role="alert">
                  {erroSlots}
                </div>
              ) : slots.length === 0 ? (
                <p className={estilos.textoAuxiliar}>
                  Nenhum horário disponível para esta data. Experimente outro dia.
                </p>
              ) : (
                <div className={estilos.gradeSlots} role="radiogroup" aria-label="Horários disponíveis">
                  {slots.map((slotIso) => {
                    const selecionado = slotSelecionado === slotIso;
                    return (
                      <button
                        key={slotIso}
                        type="button"
                        role="radio"
                        aria-checked={selecionado}
                        className={`${estilos.botaoSlot} ${selecionado ? estilos.botaoSlotSelecionado : ''}`}
                        onClick={() => setValue('slot', slotIso, { shouldValidate: true })}
                      >
                        {formatarHoraSlot(slotIso)}
                      </button>
                    );
                  })}
                </div>
              )}
              {errors.slot && <span className={estilos.mensagemErro}>{errors.slot.message}</span>}
            </fieldset>

            {/* Passo 3: Dados do cliente */}
            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>3. Seus dados</legend>

              <div className={estilos.gradeCampos}>
                <div className={estilos.campo}>
                  <label htmlFor="nomeCliente" className={estilos.rotulo}>
                    Nome completo
                  </label>
                  <input
                    id="nomeCliente"
                    type="text"
                    autoComplete="name"
                    placeholder="Seu nome"
                    className={`${estilos.entrada} ${errors.nomeCliente ? estilos.entradaErro : ''}`}
                    {...register('nomeCliente')}
                  />
                  {errors.nomeCliente && (
                    <span className={estilos.mensagemErro}>{errors.nomeCliente.message}</span>
                  )}
                </div>

                <div className={estilos.campo}>
                  <label htmlFor="emailCliente" className={estilos.rotulo}>
                    E-mail
                  </label>
                  <input
                    id="emailCliente"
                    type="email"
                    autoComplete="email"
                    placeholder="voce@email.com"
                    className={`${estilos.entrada} ${errors.emailCliente ? estilos.entradaErro : ''}`}
                    {...register('emailCliente')}
                  />
                  {errors.emailCliente && (
                    <span className={estilos.mensagemErro}>{errors.emailCliente.message}</span>
                  )}
                </div>
              </div>
            </fieldset>

            {/* Passo 4: Confirmação */}
            <fieldset className={estilos.secao}>
              <legend className={estilos.tituloSecao}>4. Confirmação</legend>

              {dataSelecionada && slotSelecionado ? (
                <div className={estilos.resumoBox}>
                  <p className={estilos.resumoLinha}>
                    <strong>Data:</strong> {formatarDataExtenso(dataSelecionada)}
                  </p>
                  <p className={estilos.resumoLinha}>
                    <strong>Horário:</strong> {formatarHoraSlot(slotSelecionado)}
                  </p>
                  {nomeCliente.trim() && (
                    <p className={estilos.resumoLinha}>
                      <strong>Cliente:</strong> {nomeCliente.trim()}
                    </p>
                  )}
                </div>
              ) : (
                <p className={estilos.textoAuxiliar}>
                  Preencha as etapas anteriores para revisar e confirmar seu agendamento.
                </p>
              )}

              {erroEnvio && (
                <div className={estilos.alertaErro} role="alert">
                  {erroEnvio}
                </div>
              )}

              <button type="submit" className={estilos.botaoPrimario} disabled={isSubmitting}>
                {isSubmitting ? 'Confirmando...' : 'Confirmar agendamento'}
              </button>
            </fieldset>
          </form>
        )}
      </section>
    </main>
  );
}
