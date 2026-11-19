import { api } from './api';
import type {
  AtualizarProfissionalRequest,
  AtualizarServicoItemRequest,
  CriarProfissionalRequest,
  CriarServicoItemRequest,
  Pagina,
  ProfissionalResponse,
  RegraDisponibilidadeResponse,
  SalvarRegraDisponibilidadeRequest,
  ServicoItemResponse,
  TenantAtualResponse,
} from '../types/gestao';

export async function obterTenantAtual(): Promise<TenantAtualResponse> {
  const resposta = await api.get<TenantAtualResponse>('/tenants/current');
  return resposta.data;
}

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

export async function atualizarStatusProfissional(id: string, ativo: boolean): Promise<ProfissionalResponse> {
  const resposta = await api.patch<ProfissionalResponse>(`/professionals/${id}/status`, { ativo });
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

export async function atualizarStatusServico(id: string, ativo: boolean): Promise<ServicoItemResponse> {
  const resposta = await api.patch<ServicoItemResponse>(`/services/${id}/status`, { ativo });
  return resposta.data;
}

export async function atualizarServico(
  id: string,
  dados: AtualizarServicoItemRequest,
): Promise<ServicoItemResponse> {
  const resposta = await api.put<ServicoItemResponse>(`/servicos/${id}`, dados);
  return resposta.data;
}

export async function listarRegras(profissionalId: string): Promise<RegraDisponibilidadeResponse[]> {
  const resposta = await api.get<RegraDisponibilidadeResponse[]>(
    `/professionals/${profissionalId}/availability-rules`,
  );
  return resposta.data;
}

export async function criarRegra(
  profissionalId: string,
  dados: SalvarRegraDisponibilidadeRequest,
): Promise<RegraDisponibilidadeResponse> {
  const resposta = await api.post<RegraDisponibilidadeResponse>(
    `/professionals/${profissionalId}/availability-rules`,
    dados,
  );
  return resposta.data;
}

export async function atualizarRegra(
  profissionalId: string,
  regraId: string,
  dados: SalvarRegraDisponibilidadeRequest,
): Promise<RegraDisponibilidadeResponse> {
  const resposta = await api.put<RegraDisponibilidadeResponse>(
    `/professionals/${profissionalId}/availability-rules/${regraId}`,
    dados,
  );
  return resposta.data;
}

export async function removerRegra(profissionalId: string, regraId: string): Promise<void> {
  await api.delete(`/professionals/${profissionalId}/availability-rules/${regraId}`);
}
