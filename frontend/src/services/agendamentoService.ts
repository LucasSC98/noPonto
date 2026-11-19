import { api } from './api';
import type {
  AgendamentoResponse,
  CriarAgendamentoRequest,
  ServicoItemResponse,
  StatusAgendamento,
} from '../types/dominio';

function cabecalhosTenantPublico(subdominio?: string): Record<string, string> {
  const tenant = subdominio?.trim() || import.meta.env.VITE_TENANT_SUBDOMINIO;
  return tenant ? { 'X-Tenant-Subdominio': tenant } : {};
}

export async function buscarServicosProfissional(
  profissionalId: string,
  subdominioTenant?: string,
): Promise<ServicoItemResponse[]> {
  const resposta = await api.get<ServicoItemResponse[]>(
    `/public/profissionais/${profissionalId}/servicos`,
    {
      headers: cabecalhosTenantPublico(subdominioTenant),
    },
  );
  return resposta.data;
}

export async function buscarSlotsDisponiveis(
  profissionalId: string,
  data: string,
  servicoId?: string,
  subdominioTenant?: string,
): Promise<string[]> {
  const params: Record<string, string> = { data };
  if (servicoId && servicoId.trim().length > 0) {
    params.servicoId = servicoId.trim();
  }

  const resposta = await api.get<string[]>(`/public/profissionais/${profissionalId}/slots`, {
    params,
    headers: cabecalhosTenantPublico(subdominioTenant),
  });
  return resposta.data;
}

export async function criarAgendamentoPublico(
  requisicao: CriarAgendamentoRequest,
  subdominioTenant?: string,
): Promise<AgendamentoResponse> {
  const resposta = await api.post<AgendamentoResponse>('/public/agendamentos', requisicao, {
    headers: cabecalhosTenantPublico(subdominioTenant),
  });
  return resposta.data;
}

export async function listarAgendamentos(): Promise<AgendamentoResponse[]> {
  const resposta = await api.get<AgendamentoResponse[]>('/agendamentos');
  return resposta.data;
}

export async function atualizarStatusAgendamento(
  agendamentoId: string,
  status: StatusAgendamento,
): Promise<AgendamentoResponse> {
  const resposta = await api.patch<AgendamentoResponse>(
    `/agendamentos/${agendamentoId}/status`,
    { status },
  );
  return resposta.data;
}
