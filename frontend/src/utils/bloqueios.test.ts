import { describe, expect, it } from 'vitest';
import type { AgendamentoResponse } from '../types/dominio';
import type { BloqueioResponse } from '../types/gestao';
import {
  bloqueioParaFormulario,
  ehBloqueioDiaInteiro,
  encontrarBloqueiosConflitantesComAgendamento,
  formatarPeriodoBloqueio,
  montarIntervaloBloqueioUtc,
  separarBloqueiosFuturosEHistorico,
  unirBloqueiosSobrepostos,
  validarFormularioBloqueio,
} from './bloqueios';

function criarBloqueio(
  id: string,
  inicio: string,
  fim: string,
  motivo: string | null = null,
  profissionalId = 'prof-1',
): BloqueioResponse {
  return { id, profissionalId, inicio, fim, motivo };
}

describe('bloqueios', () => {
  it('deve_converterDiaInteiroEPeriodoDeHorasParaUtc_quando_montarIntervalo', () => {
    const diaInteiro = montarIntervaloBloqueioUtc({
      dataInicio: '2026-12-25',
      dataFim: '2026-12-25',
      diaInteiro: true,
      horaInicio: '08:00',
      horaFim: '18:00',
    });
    expect(diaInteiro).toEqual({
      inicio: '2026-12-25T03:00:00.000Z',
      fim: '2026-12-26T03:00:00.000Z',
    });

    const ferias = montarIntervaloBloqueioUtc({
      dataInicio: '2026-12-24',
      dataFim: '2026-12-26',
      diaInteiro: true,
      horaInicio: '08:00',
      horaFim: '18:00',
    });
    expect(ferias).toEqual({
      inicio: '2026-12-24T03:00:00.000Z',
      fim: '2026-12-27T03:00:00.000Z',
    });

    const faixa = montarIntervaloBloqueioUtc({
      dataInicio: '2026-12-10',
      dataFim: '2026-12-10',
      diaInteiro: false,
      horaInicio: '14:00',
      horaFim: '17:30',
    });
    expect(faixa).toEqual({
      inicio: '2026-12-10T17:00:00.000Z',
      fim: '2026-12-10T20:30:00.000Z',
    });
  });

  it('deve_rejeitarFimMenorOuIgualAoInicioEMotivoMaiorQue120Caracteres', () => {
    const horaInvalida = validarFormularioBloqueio({
      dataInicio: '2026-12-10',
      dataFim: '2026-12-10',
      diaInteiro: false,
      horaInicio: '15:00',
      horaFim: '14:00',
      motivo: 'Reunião',
    });
    expect(horaInvalida.valido).toBe(false);
    expect(horaInvalida.erros.horaFim).toBe('A hora de fim deve ser maior que a hora de início.');

    const motivoLongo = validarFormularioBloqueio({
      dataInicio: '2026-12-10',
      dataFim: '2026-12-10',
      diaInteiro: true,
      horaInicio: '08:00',
      horaFim: '18:00',
      motivo: 'x'.repeat(121),
    });
    expect(motivoLongo.valido).toBe(false);
    expect(motivoLongo.erros.motivo).toBe('O motivo deve ter no máximo 120 caracteres.');
  });

  it('deve_identificarEFormatarDiaInteiroEFaixaDeHorasCorretamente', () => {
    const umDia = criarBloqueio('1', '2026-12-25T03:00:00Z', '2026-12-26T03:00:00Z', 'Natal');
    expect(ehBloqueioDiaInteiro(umDia.inicio, umDia.fim)).toBe(true);
    expect(formatarPeriodoBloqueio(umDia.inicio, umDia.fim)).toBe('25/12/2026 · Dia inteiro');
    expect(bloqueioParaFormulario(umDia)).toEqual({
      dataInicio: '2026-12-25',
      dataFim: '2026-12-25',
      diaInteiro: true,
      horaInicio: '08:00',
      horaFim: '18:00',
      motivo: 'Natal',
    });

    const variosDias = criarBloqueio('2', '2026-12-24T03:00:00Z', '2026-12-27T03:00:00Z');
    expect(formatarPeriodoBloqueio(variosDias.inicio, variosDias.fim)).toBe(
      '24/12/2026 a 26/12/2026 · Dia inteiro',
    );

    const faixa = criarBloqueio('3', '2026-12-10T17:00:00Z', '2026-12-10T20:00:00Z');
    expect(ehBloqueioDiaInteiro(faixa.inicio, faixa.fim)).toBe(false);
    expect(formatarPeriodoBloqueio(faixa.inicio, faixa.fim)).toBe('10/12/2026 · 14:00 – 17:00');
  });

  it('deve_separarBloqueiosFuturosEHistorico_e_unirSobrepostosNaExibicao', () => {
    const agora = new Date('2026-11-20T15:00:00Z');
    const passado = criarBloqueio('p1', '2026-11-10T12:00:00Z', '2026-11-10T15:00:00Z', 'Passado');
    const futuroA = criarBloqueio('f1', '2026-12-01T12:00:00Z', '2026-12-01T15:00:00Z', 'Manhã');
    const futuroB = criarBloqueio('f2', '2026-12-01T14:00:00Z', '2026-12-01T18:00:00Z', 'Tarde');
    const futuroC = criarBloqueio('f3', '2026-12-01T18:00:00Z', '2026-12-01T20:00:00Z', 'Adjacente');

    const { futuros, historico } = separarBloqueiosFuturosEHistorico(
      [futuroB, passado, futuroC, futuroA],
      agora,
    );
    expect(historico.map((b) => b.id)).toEqual(['p1']);
    expect(futuros.map((b) => b.id)).toEqual(['f1', 'f2', 'f3']);

    const grupos = unirBloqueiosSobrepostos(futuros);
    expect(grupos).toHaveLength(2);
    expect(grupos[0].inicio).toBe('2026-12-01T12:00:00Z');
    expect(grupos[0].fim).toBe('2026-12-01T18:00:00Z');
    expect(grupos[0].bloqueios.map((b) => b.id)).toEqual(['f1', 'f2']);
    expect(grupos[1].bloqueios.map((b) => b.id)).toEqual(['f3']);
  });

  it('deve_detectarConflitoApenasEmAgendamentosAtivosNoPeriodoBloqueado', () => {
    const bloqueio = criarBloqueio('b1', '2026-12-10T12:00:00Z', '2026-12-10T15:00:00Z', 'Folga');
    const baseAgendamento: AgendamentoResponse = {
      id: 'ag-1',
      profissionalId: 'prof-1',
      servicoItemId: 'srv-1',
      nomeCliente: 'Maria',
      emailCliente: 'maria@teste.com',
      inicio: '2026-12-10T13:00:00Z',
      fim: '2026-12-10T13:30:00Z',
      status: 'CONFIRMADO',
    };

    expect(encontrarBloqueiosConflitantesComAgendamento(baseAgendamento, [bloqueio])).toHaveLength(1);
    expect(
      encontrarBloqueiosConflitantesComAgendamento(
        { ...baseAgendamento, status: 'PENDENTE' },
        [bloqueio],
      ),
    ).toHaveLength(1);
    expect(
      encontrarBloqueiosConflitantesComAgendamento(
        { ...baseAgendamento, status: 'CANCELADO' },
        [bloqueio],
      ),
    ).toHaveLength(0);
    expect(
      encontrarBloqueiosConflitantesComAgendamento(
        { ...baseAgendamento, inicio: '2026-12-10T15:00:00Z', fim: '2026-12-10T15:30:00Z' },
        [bloqueio],
      ),
    ).toHaveLength(0);
  });
});
