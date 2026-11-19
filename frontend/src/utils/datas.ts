import { format, isBefore, isValid, parseISO, startOfDay } from 'date-fns';
import { ptBR } from 'date-fns/locale';

/** Retorna a data local atual no formato YYYY-MM-DD (compatível com input[type="date"]). */
export function dataHojeIsoLocal(referencia: Date = new Date()): string {
  return format(referencia, 'yyyy-MM-dd');
}

/** Verifica se uma data YYYY-MM-DD está antes do dia atual no fuso local. */
export function dataNoPassado(dataIso: string, referencia: Date = new Date()): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dataIso)) {
    return false;
  }
  const dataInformada = parseISO(dataIso);
  if (!isValid(dataInformada)) {
    return false;
  }
  return isBefore(startOfDay(dataInformada), startOfDay(referencia));
}

/** Converte um instante UTC (ISO-8601) para horário local HH:mm. */
export function formatarHoraSlot(instanteIso: string): string {
  return format(parseISO(instanteIso), 'HH:mm', { locale: ptBR });
}

/** Converte um instante UTC (ISO-8601) para data e hora locais legíveis. */
export function formatarDataHoraLocal(instanteIso: string): string {
  return format(parseISO(instanteIso), "dd/MM/yyyy 'às' HH:mm", { locale: ptBR });
}

/** Formata uma data YYYY-MM-DD em formato por extenso em português. */
export function formatarDataExtenso(dataIso: string): string {
  return format(parseISO(dataIso), "EEEE, dd 'de' MMMM 'de' yyyy", { locale: ptBR });
}
