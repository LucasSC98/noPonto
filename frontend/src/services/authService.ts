import { api, CHAVE_TOKEN_STORAGE } from './api';
import type { LoginRequest, LoginResponse } from '../types/dominio';

export async function autenticar(credenciais: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', credenciais);
  salvarToken(data.token);
  return data;
}

export function salvarToken(token: string): void {
  localStorage.setItem(CHAVE_TOKEN_STORAGE, token);
}

export function obterToken(): string | null {
  return localStorage.getItem(CHAVE_TOKEN_STORAGE);
}

export function removerToken(): void {
  localStorage.removeItem(CHAVE_TOKEN_STORAGE);
}

export function estaAutenticado(): boolean {
  const token = obterToken();
  return token !== null && token.trim().length > 0;
}
