import type { AgendamentoResponse } from '../types/dominio';
import type { BloqueioResponse } from '../types/gestao';

export const OFFSET_ESTABELECIMENTO = '-03:00';
export const TAMANHO_MAXIMO_MOTIVO_BLOQUEIO = 120;

const DESLOCAMENTO_ESTABELECIMENTO_MS = -3 * 60 * 60 * 1000;
const UM_DIA_MS = 24 * 60 * 60 * 1000;

export interface DadosFormularioBloqueio {
  dataInicio: string;
  dataFim: string;
  diaInteiro: boolean;
  horaInicio: string;
  horaFim: string;
  motivo: string;
}

export interface ErrosFormularioBloqueio {
  dataInicio?: string;
  dataFim?: string;
  horaInicio?: string;
  horaFim?: string;
  motivo?: string;
}

export interface GrupoBloqueioExibicao {
  inicio: string;
  fim: string;
  bloqueios: BloqueioResponse[];
}

interface PartesEstabelecimento {
  dataIso: string;
  dataBr: string;
  hora: string;
  segundos: number;
}

function pad2(valor: number): string {
  return String(valor).padStart(2, '0');
}

function formatarDataBr(dataIso: string): string {
  const [ano, mes, dia] = dataIso.split('-');
  return `${dia}/${mes}/${ano}`;
}

function somarDiasDataIso(dataIso: string, dias: number): string {
  const [ano, mes, dia] = dataIso.split('-').map(Number);
  const data = new Date(Date.UTC(ano, mes - 1, dia + dias));
  return `${data.getUTCFullYear()}-${pad2(data.getUTCMonth() + 1)}-${pad2(data.getUTCDate())}`;
}

function dataHoraEstabelecimentoParaUtcIso(dataIso: string, hora: string): string {
  return new Date(`${dataIso}T${hora}:00${OFFSET_ESTABELECIMENTO}`).toISOString();
}

function isoParaPartesEstabelecimento(instanteIso: string): PartesEstabelecimento {
  const deslocado = new Date(Date.parse(instanteIso) + DESLOCAMENTO_ESTABELECIMENTO_MS);
  const ano = deslocado.getUTCFullYear();
  const mes = pad2(deslocado.getUTCMonth() + 1);
  const dia = pad2(deslocado.getUTCDate());
  const hora = pad2(deslocado.getUTCHours());
  const minuto = pad2(deslocado.getUTCMinutes());
  const segundos = deslocado.getUTCSeconds();
  const dataIso = `${ano}-${mes}-${dia}`;
  return {
    dataIso,
    dataBr: `${dia}/${mes}/${ano}`,
    hora: `${hora}:${minuto}`,
    segundos,
  };
}

/**
 * Converte os campos do formulário (interpretados no fuso do estabelecimento -03:00,
 * como em RegraDisponibilidade) para o intervalo semiaberto [inicio, fim) em UTC.
 */
export function montarIntervaloBloqueioUtc(dados: {
  dataInicio: string;
  dataFim: string;
  diaInteiro: boolean;
  horaInicio: string;
  horaFim: string;
}): { inicio: string; fim: string } {
  if (dados.diaInteiro) {
    return {
      inicio: dataHoraEstabelecimentoParaUtcIso(dados.dataInicio, '00:00'),
      fim: dataHoraEstabelecimentoParaUtcIso(somarDiasDataIso(dados.dataFim, 1), '00:00'),
    };
  }
  return {
    inicio: dataHoraEstabelecimentoParaUtcIso(dados.dataInicio, dados.horaInicio),
    fim: dataHoraEstabelecimentoParaUtcIso(dados.dataFim, dados.horaFim),
  };
}

export function validarFormularioBloqueio(dados: DadosFormularioBloqueio): {
  erros: ErrosFormularioBloqueio;
  valido: boolean;
} {
  const erros: ErrosFormularioBloqueio = {};
  const regexData = /^\d{4}-\d{2}-\d{2}$/;
  const regexHora = /^\d{2}:\d{2}$/;

  const dataInicioValida = regexData.test(dados.dataInicio);
  const dataFimValida = regexData.test(dados.dataFim);

  if (!dataInicioValida) {
    erros.dataInicio = 'Informe a data inicial.';
  }
  if (!dataFimValida) {
    erros.dataFim = 'Informe a data final.';
  }

  if (dataInicioValida && dataFimValida && dados.dataFim < dados.dataInicio) {
    erros.dataFim = 'A data final não pode ser anterior à data inicial.';
  }

  if (!dados.diaInteiro) {
    const horaInicioValida = regexHora.test(dados.horaInicio);
    const horaFimValida = regexHora.test(dados.horaFim);

    if (!horaInicioValida) {
      erros.horaInicio = 'Informe a hora inicial.';
    }
    if (!horaFimValida) {
      erros.horaFim = 'Informe a hora final.';
    }

    if (dataInicioValida && dataFimValida && !erros.dataFim && horaInicioValida && horaFimValida) {
      const { inicio, fim } = montarIntervaloBloqueioUtc(dados);
      if (Date.parse(fim) <= Date.parse(inicio)) {
        if (dados.dataInicio === dados.dataFim) {
          erros.horaFim = 'A hora de fim deve ser maior que a hora de início.';
        } else {
          erros.dataFim = 'O fim do bloqueio deve ser maior que o início.';
        }
      }
    }
  }

  if (dados.motivo.trim().length > TAMANHO_MAXIMO_MOTIVO_BLOQUEIO) {
    erros.motivo = `O motivo deve ter no máximo ${TAMANHO_MAXIMO_MOTIVO_BLOQUEIO} caracteres.`;
  }

  return {
    erros,
    valido: Object.keys(erros).length === 0,
  };
}

export function ehBloqueioDiaInteiro(inicioIso: string, fimIso: string): boolean {
  const inicio = isoParaPartesEstabelecimento(inicioIso);
  const fim = isoParaPartesEstabelecimento(fimIso);
  const duracaoMs = Date.parse(fimIso) - Date.parse(inicioIso);
  return (
    duracaoMs >= UM_DIA_MS &&
    inicio.hora === '00:00' &&
    inicio.segundos === 0 &&
    fim.hora === '00:00' &&
    fim.segundos === 0
  );
}

export function bloqueioParaFormulario(bloqueio: BloqueioResponse): DadosFormularioBloqueio {
  const inicio = isoParaPartesEstabelecimento(bloqueio.inicio);
  const fim = isoParaPartesEstabelecimento(bloqueio.fim);
  const diaInteiro = ehBloqueioDiaInteiro(bloqueio.inicio, bloqueio.fim);

  if (diaInteiro) {
    return {
      dataInicio: inicio.dataIso,
      dataFim: somarDiasDataIso(fim.dataIso, -1),
      diaInteiro: true,
      horaInicio: '08:00',
      horaFim: '18:00',
      motivo: bloqueio.motivo ?? '',
    };
  }

  return {
    dataInicio: inicio.dataIso,
    dataFim: fim.dataIso,
    diaInteiro: false,
    horaInicio: inicio.hora,
    horaFim: fim.hora,
    motivo: bloqueio.motivo ?? '',
  };
}

export function formatarPeriodoBloqueio(inicioIso: string, fimIso: string): string {
  const inicio = isoParaPartesEstabelecimento(inicioIso);
  const fim = isoParaPartesEstabelecimento(fimIso);

  if (ehBloqueioDiaInteiro(inicioIso, fimIso)) {
    const ultimoDiaInclusivo = somarDiasDataIso(fim.dataIso, -1);
    if (ultimoDiaInclusivo === inicio.dataIso) {
      return `${inicio.dataBr} · Dia inteiro`;
    }
    return `${inicio.dataBr} a ${formatarDataBr(ultimoDiaInclusivo)} · Dia inteiro`;
  }

  if (inicio.dataIso === fim.dataIso) {
    return `${inicio.dataBr} · ${inicio.hora} – ${fim.hora}`;
  }
  return `${inicio.dataBr} ${inicio.hora} a ${fim.dataBr} ${fim.hora}`;
}

export function separarBloqueiosFuturosEHistorico(
  bloqueios: BloqueioResponse[],
  agora: Date = new Date(),
): {
  futuros: BloqueioResponse[];
  historico: BloqueioResponse[];
} {
  const agoraMs = agora.getTime();
  const futuros: BloqueioResponse[] = [];
  const historico: BloqueioResponse[] = [];

  for (const bloqueio of bloqueios) {
    if (Date.parse(bloqueio.fim) > agoraMs) {
      futuros.push(bloqueio);
    } else {
      historico.push(bloqueio);
    }
  }

  futuros.sort(
    (a, b) => Date.parse(a.inicio) - Date.parse(b.inicio) || Date.parse(a.fim) - Date.parse(b.fim),
  );
  historico.sort(
    (a, b) => Date.parse(b.inicio) - Date.parse(a.inicio) || Date.parse(b.fim) - Date.parse(a.fim),
  );

  return { futuros, historico };
}

/**
 * Une visualmente bloqueios sobrepostos em um mesmo período contínuo, mantendo os registros
 * individuais acessíveis para edição e remoção. Intervalos apenas adjacentes (fim == início)
 * permanecem separados.
 */
export function unirBloqueiosSobrepostos(
  bloqueios: BloqueioResponse[],
): GrupoBloqueioExibicao[] {
  if (bloqueios.length === 0) {
    return [];
  }

  const ordenados = [...bloqueios].sort(
    (a, b) => Date.parse(a.inicio) - Date.parse(b.inicio) || Date.parse(a.fim) - Date.parse(b.fim),
  );

  const grupos: GrupoBloqueioExibicao[] = [];
  for (const bloqueio of ordenados) {
    const ultimoGrupo = grupos[grupos.length - 1];
    if (ultimoGrupo && Date.parse(bloqueio.inicio) < Date.parse(ultimoGrupo.fim)) {
      if (Date.parse(bloqueio.fim) > Date.parse(ultimoGrupo.fim)) {
        ultimoGrupo.fim = bloqueio.fim;
      }
      ultimoGrupo.bloqueios.push(bloqueio);
    } else {
      grupos.push({
        inicio: bloqueio.inicio,
        fim: bloqueio.fim,
        bloqueios: [bloqueio],
      });
    }
  }

  return grupos;
}

export function encontrarBloqueiosConflitantesComAgendamento(
  agendamento: AgendamentoResponse,
  bloqueios: BloqueioResponse[],
): BloqueioResponse[] {
  if (agendamento.status !== 'PENDENTE' && agendamento.status !== 'CONFIRMADO') {
    return [];
  }

  const inicioAgendamento = Date.parse(agendamento.inicio);
  const fimAgendamento = Date.parse(agendamento.fim);

  return bloqueios.filter((bloqueio) => {
    if (bloqueio.profissionalId !== agendamento.profissionalId) {
      return false;
    }
    return (
      Date.parse(bloqueio.inicio) < fimAgendamento &&
      Date.parse(bloqueio.fim) > inicioAgendamento
    );
  });
}
