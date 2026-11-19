import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import estilos from './MenuAcoes.module.css';

export interface ItemMenuAcao {
  rotulo: string;
  aoClicar: () => void;
  perigo?: boolean;
  desabilitado?: boolean;
}

interface MenuAcoesProps {
  rotuloAcessivel: string;
  itens: ItemMenuAcao[];
}

export function MenuAcoes({ rotuloAcessivel, itens }: MenuAcoesProps) {
  const [aberto, setAberto] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const gatilhoRef = useRef<HTMLButtonElement>(null);
  const itensRef = useRef<Array<HTMLButtonElement | null>>([]);

  useEffect(() => {
    if (!aberto) {
      return;
    }

    function aoClicarFora(evento: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(evento.target as Node)) {
        setAberto(false);
      }
    }

    function aoPressionarTecla(evento: globalThis.KeyboardEvent) {
      if (evento.key === 'Escape') {
        setAberto(false);
        gatilhoRef.current?.focus();
      }
    }

    document.addEventListener('mousedown', aoClicarFora);
    document.addEventListener('keydown', aoPressionarTecla);
    return () => {
      document.removeEventListener('mousedown', aoClicarFora);
      document.removeEventListener('keydown', aoPressionarTecla);
    };
  }, [aberto]);

  useEffect(() => {
    if (aberto) {
      const primeiroHabilitado = itensRef.current.find((botao) => botao && !botao.disabled);
      primeiroHabilitado?.focus();
    }
  }, [aberto]);

  function aoNavegarMenu(evento: KeyboardEvent<HTMLDivElement>) {
    const botoes = itensRef.current.filter(
      (botao): botao is HTMLButtonElement => botao !== null && !botao.disabled,
    );
    if (botoes.length === 0) {
      return;
    }

    const indiceAtual = botoes.indexOf(document.activeElement as HTMLButtonElement);
    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      botoes[(indiceAtual + 1) % botoes.length].focus();
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      botoes[(indiceAtual - 1 + botoes.length) % botoes.length].focus();
    } else if (evento.key === 'Tab') {
      setAberto(false);
    }
  }

  return (
    <div ref={containerRef} className={estilos.wrapper}>
      <button
        ref={gatilhoRef}
        type="button"
        className={estilos.gatilho}
        aria-label={rotuloAcessivel}
        aria-haspopup="menu"
        aria-expanded={aberto}
        onClick={() => setAberto((atual) => !atual)}
      >
        ⋯
      </button>

      {aberto && (
        <div
          role="menu"
          aria-label={rotuloAcessivel}
          className={estilos.menu}
          onKeyDown={aoNavegarMenu}
        >
          {itens.map((item, indice) => (
            <button
              key={item.rotulo}
              ref={(elemento) => {
                itensRef.current[indice] = elemento;
              }}
              type="button"
              role="menuitem"
              disabled={item.desabilitado}
              className={`${estilos.item} ${item.perigo ? estilos.itemPerigo : ''}`}
              onClick={() => {
                setAberto(false);
                item.aoClicar();
              }}
            >
              {item.rotulo}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
