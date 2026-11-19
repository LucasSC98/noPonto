import { describe, expect, it } from 'vitest';
import { dataHojeIsoLocal, dataNoPassado } from './datas';
import { esquemaAgendamentoPublico, esquemaLogin } from './validacao';

describe('datas', () => {
  const referencia = new Date(2026, 10, 18, 14, 30, 0); // 18/11/2026 14:30 local

  it('deve_retornarVerdadeiro_quando_dataAnteriorAHoje', () => {
    expect(dataNoPassado('2026-11-17', referencia)).toBe(true);
  });

  it('deve_retornarFalso_quando_dataIgualOuPosteriorAHoje', () => {
    expect(dataNoPassado('2026-11-18', referencia)).toBe(false);
    expect(dataNoPassado('2026-11-19', referencia)).toBe(false);
  });

  it('deve_formatarDataHojeIsoLocal_quando_referenciaInformada', () => {
    expect(dataHojeIsoLocal(referencia)).toBe('2026-11-18');
  });
});

describe('validacao', () => {
  const dadosValidos = {
    data: '2099-12-31',
    servicoItemId: '550e8400-e29b-41d4-a716-446655440000',
    slot: '2099-12-31T14:00:00Z',
    nomeCliente: 'Maria Silva',
    emailCliente: 'maria@email.com',
  };

  it('deve_validarAgendamento_quando_todosOsCamposValidos', () => {
    const resultado = esquemaAgendamentoPublico.safeParse(dadosValidos);
    expect(resultado.success).toBe(true);
  });

  it('deve_rejeitarAgendamento_quando_dataNoPassado', () => {
    const resultado = esquemaAgendamentoPublico.safeParse({
      ...dadosValidos,
      data: '2020-01-01',
    });
    expect(resultado.success).toBe(false);
  });

  it('deve_rejeitarAgendamento_quando_slotAusente', () => {
    const resultado = esquemaAgendamentoPublico.safeParse({
      ...dadosValidos,
      slot: '',
    });
    expect(resultado.success).toBe(false);
  });

  it('deve_rejeitarAgendamento_quando_nomeOuEmailInvalido', () => {
    expect(
      esquemaAgendamentoPublico.safeParse({ ...dadosValidos, nomeCliente: '' }).success,
    ).toBe(false);
    expect(
      esquemaAgendamentoPublico.safeParse({ ...dadosValidos, emailCliente: 'invalido' }).success,
    ).toBe(false);
  });

  it('deve_validarLogin_quando_emailESenhaPreenchidos', () => {
    expect(esquemaLogin.safeParse({ email: 'admin@clinica.com', senha: '123' }).success).toBe(true);
    expect(esquemaLogin.safeParse({ email: 'invalido', senha: '' }).success).toBe(false);
  });
});
