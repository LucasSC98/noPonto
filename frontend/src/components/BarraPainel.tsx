import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import estilos from '../pages/PaginaDashboard.module.css';

const LINKS = [
  { para: '/dashboard', rotulo: 'Agenda' },
  { para: '/profissionais', rotulo: 'Profissionais' },
  { para: '/servicos', rotulo: 'Serviços' },
] as const;

interface BarraPainelProps {
  atual: (typeof LINKS)[number]['para'];
  children?: ReactNode;
}

export function BarraPainel({ atual, children }: BarraPainelProps) {
  const navegar = useNavigate();

  return (
    <header className={estilos.barraSuperior}>
      <div className={estilos.barraConteudo}>
        <div className={estilos.identidade}>
          <span className={estilos.marca}>NoPonto</span>
          <nav className={estilos.navegacao} aria-label="Painel">
            {LINKS.map((link) => (
              <button
                key={link.para}
                type="button"
                className={atual === link.para ? estilos.linkNavAtivo : estilos.linkNav}
                onClick={() => navegar(link.para)}
              >
                {link.rotulo}
              </button>
            ))}
          </nav>
        </div>
        {children && <div className={estilos.acoesBarra}>{children}</div>}
      </div>
    </header>
  );
}
