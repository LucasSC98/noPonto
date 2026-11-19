import estilos from './BadgeStatus.module.css';

interface BadgeAtivoProps {
  ativo: boolean;
}

export function BadgeAtivo({ ativo }: BadgeAtivoProps) {
  return (
    <span className={`${estilos.badge} ${ativo ? estilos.concluido : estilos.cancelado}`}>
      {ativo ? 'Ativo' : 'Inativo'}
    </span>
  );
}
