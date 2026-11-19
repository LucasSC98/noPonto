import { ROTULO_STATUS } from '../types/dominio';
import type { StatusAgendamento } from '../types/dominio';
import estilos from './BadgeStatus.module.css';

interface BadgeStatusProps {
  status: StatusAgendamento;
}

const CLASSE_POR_STATUS: Record<StatusAgendamento, string> = {
  PENDENTE: estilos.pendente,
  CONFIRMADO: estilos.confirmado,
  CANCELADO: estilos.cancelado,
  CONCLUIDO: estilos.concluido,
};

export function BadgeStatus({ status }: BadgeStatusProps) {
  return (
    <span className={`${estilos.badge} ${CLASSE_POR_STATUS[status]}`}>
      {ROTULO_STATUS[status]}
    </span>
  );
}
