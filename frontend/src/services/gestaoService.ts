import { api } from './api';
import type {
  AtualizarProfissionalRequest,
  AtualizarRegraDisponibilidadeRequest,
  AtualizarServicoItemRequest,
  CriarProfissionalRequest,
  CriarRegraDisponibilidadeRequest,
  CriarServicoItemRequest,
  Pagina,
  ProfissionalResponse,
  RegraDisponibilidadeResponse,
  ServicoItemResponse,
} from '../types/gestao';

export async function listarProfissionais(page = 0, size = 20): Promise<Pagina<ProfissionalResponse>> {
  const resposta = await api.get<Pagina<ProfissionalResponse>>('/profissionais', {
    params: { page, size, sort: 'nome,asc' },
  });
  return resposta.data;
}

export async function criarProfissional(dados: CriarProfissionalRequest): Promise<ProfissionalResponse> {
  const resposta = await api.post<ProfissionalResponse>('/profissionais', dados);
  return resposta.data;
}

export async function atualizarProfissional(
  id: string,
  dados: AtualizarProfissionalRequest,
): Promise<ProfissionalResponse> {
  const resposta = await api.put<ProfissionalResponse>(`/profissionais/${id}`, dados);
  return resposta.data;
}

export async function listarServicos(
  profissionalId: string,
  page = 0,
  size = 20,
): Promise<Pagina<ServicoItemResponse>> {
  const resposta = await api.get<Pagina<ServicoItemResponse>>('/servicos', {
    params: { profissionalId, page, size, sort: 'nome,asc' },
  });
  return resposta.data;
}

export async function criarServico(dados: CriarServicoItemRequest): Promise<ServicoItemResponse> {
  const resposta = await api.post<ServicoItemResponse>('/servicos', dados);
  return resposta.data;
}

export async function atualizarServico(
  id: string,
  dados: AtualizarServicoItemRequest,
): Promise<ServicoItemResponse> {
  const resposta = await api.put<ServicoItemResponse>(`/servicos/${id}`, dados);
  return resposta.data;
}

export async function listarRegras(
  profissionalId: string,
  page = 0,
  size = 20,
): Promise<Pagina<RegraDisponibilidadeResponse>> {
  const resposta = await api.get<Pagina<RegraDisponibilidadeResponse>>('/regras-disponibilidade', {
    params: { profissionalId, page, size, sort: 'diaSemana,asc' },
  });
  return resposta.data;
}

export async function criarRegra(
  dados: CriarRegraDisponibilidadeRequest,
): Promise<RegraDisponibilidadeResponse> {
  const resposta = await api.post<RegraDisponibilidadeResponse>('/regras-disponibilidade', dados);
  return resposta.data;
}

export async function atualizarRegra(
  id: string,
  dados: AtualizarRegraDisponibilidadeRequest,
): Promise<RegraDisponibilidadeResponse> {
  const resposta = await api.put<RegraDisponibilidadeResponse>(`/regras-disponibilidade/${id}`, dados);
  return resposta.data;
}
