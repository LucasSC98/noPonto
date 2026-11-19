import axios from 'axios';
import type { ErroApiResponse } from '../types/dominio';

export const CHAVE_TOKEN_STORAGE = 'noponto_token';
export const TENANT_PUBLICO_PADRAO = import.meta.env.VITE_TENANT_SUBDOMINIO || 'clinica-teste';

const URL_BASE_API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const api = axios.create({
  baseURL: URL_BASE_API,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(CHAVE_TOKEN_STORAGE);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  const url = config.url ?? '';
  if (url.startsWith('/public/')) {
    const paramsAtuais =
      config.params && typeof config.params === 'object'
        ? (config.params as Record<string, unknown>)
        : {};
    const possuiTenantNaUrl = /[?&]tenant=/.test(url);
    if (!possuiTenantNaUrl && !paramsAtuais.tenant) {
      config.params = { ...paramsAtuais, tenant: TENANT_PUBLICO_PADRAO };
    }
  }

  return config;
});

export function extrairMensagemErro(erro: unknown, mensagemPadrao: string): string {
  if (axios.isAxiosError<ErroApiResponse>(erro)) {
    const mensagemApi = erro.response?.data?.mensagem;
    if (mensagemApi && mensagemApi.trim().length > 0) {
      return mensagemApi;
    }
    if (erro.response?.status === 401) {
      return 'Sessão expirada ou credenciais inválidas.';
    }
    if (!erro.response) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
  }
  return mensagemPadrao;
}
