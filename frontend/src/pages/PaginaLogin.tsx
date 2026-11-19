import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Navigate, useNavigate } from 'react-router-dom';
import { autenticar, estaAutenticado } from '../services/authService';
import { extrairMensagemErro } from '../services/api';
import { esquemaLogin } from '../utils/validacao';
import type { DadosLogin } from '../utils/validacao';
import estilos from './PaginaLogin.module.css';

export function PaginaLogin() {
  const navegar = useNavigate();
  const [erroAutenticacao, setErroAutenticacao] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<DadosLogin>({
    resolver: zodResolver(esquemaLogin),
    defaultValues: {
      email: '',
      senha: '',
    },
  });

  if (estaAutenticado()) {
    return <Navigate to="/dashboard" replace />;
  }

  async function aoEnviar(dados: DadosLogin) {
    setErroAutenticacao(null);
    try {
      await autenticar(dados);
      navegar('/dashboard', { replace: true });
    } catch (erro) {
      setErroAutenticacao(extrairMensagemErro(erro, 'E-mail ou senha inválidos.'));
    }
  }

  return (
    <main className={estilos.container}>
      <section className={estilos.card}>
        <header className={estilos.cabecalho}>
          <span className={estilos.marca}>NoPonto</span>
          <h1 className={estilos.titulo}>Acesso administrativo</h1>
          <p className={estilos.subtitulo}>
            Entre com suas credenciais para gerenciar os agendamentos.
          </p>
        </header>

        {erroAutenticacao && (
          <div className={estilos.alertaErro} role="alert">
            {erroAutenticacao}
          </div>
        )}

        <form className={estilos.formulario} onSubmit={handleSubmit(aoEnviar)} noValidate>
          <div className={estilos.campo}>
            <label htmlFor="email" className={estilos.rotulo}>
              E-mail
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="nome@clinica.com"
              className={`${estilos.entrada} ${errors.email ? estilos.entradaErro : ''}`}
              {...register('email')}
            />
            {errors.email && <span className={estilos.mensagemErro}>{errors.email.message}</span>}
          </div>

          <div className={estilos.campo}>
            <label htmlFor="senha" className={estilos.rotulo}>
              Senha
            </label>
            <input
              id="senha"
              type="password"
              autoComplete="current-password"
              placeholder="Sua senha"
              className={`${estilos.entrada} ${errors.senha ? estilos.entradaErro : ''}`}
              {...register('senha')}
            />
            {errors.senha && <span className={estilos.mensagemErro}>{errors.senha.message}</span>}
          </div>

          <button type="submit" className={estilos.botaoPrimario} disabled={isSubmitting}>
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      </section>
    </main>
  );
}
