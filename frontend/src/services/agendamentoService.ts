import { api } from './api';
import type {
  AgendamentoAutoatendimentoResponse,
  AgendamentoResponse,
  CriarAgendamentoRequest,
  ProfissionalPublicoResponse,
  ServicoItemResponse,
  StatusAgendamento,
} from '../types/dominio';

function parametrosTenantPublico(subdominio?: string): Record<string, string> {
  const tenant = subdominio?.trim();
  return tenant ? { tenant } : {};
}

export async function buscarProfissionalPublico(
  profissionalId: string,
  subdominioTenant?: string,
): Promise<ProfissionalPublicoResponse> {
  const resposta = await api.get<ProfissionalPublicoResponse>(
    `/public/profissionais/${profissionalId}`,
    {
      params: parametrosTenantPublico(subdominioTenant),
    },
  );
  return resposta.data;
}

export async function buscarServicosProfissional(
  profissionalId: string,
  subdominioTenant?: string,
): Promise<ServicoItemResponse[]> {
  const resposta = await api.get<ServicoItemResponse[]>(
    `/public/profissionais/${profissionalId}/servicos`,
    {
      params: parametrosTenantPublico(subdominioTenant),
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
  const params: Record<string, string> = {
    data,
    ...parametrosTenantPublico(subdominioTenant),
  };
  if (servicoId && servicoId.trim().length > 0) {
    params.servicoId = servicoId.trim();
  }

  const resposta = await api.get<string[]>(`/public/profissionais/${profissionalId}/slots`, {
    params,
  });
  return resposta.data;
}

export async function criarAgendamentoPublico(
  requisicao: CriarAgendamentoRequest,
  subdominioTenant?: string,
): Promise<AgendamentoResponse> {
  const resposta = await api.post<AgendamentoResponse>('/public/agendamentos', requisicao, {
    params: parametrosTenantPublico(subdominioTenant),
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

export async function consultarAgendamentoPorToken(
  token: string,
): Promise<AgendamentoAutoatendimentoResponse> {
  const resposta = await api.get<AgendamentoAutoatendimentoResponse>(
    `/public/appointments/manage/${encodeURIComponent(token)}`,
  );
  return resposta.data;
}

export async function buscarSlotsAutoatendimento(
  token: string,
  data: string,
): Promise<string[]> {
  const resposta = await api.get<string[]>(
    `/public/appointments/manage/${encodeURIComponent(token)}/slots`,
    { params: { data } },
  );
  return resposta.data;
}

export async function cancelarAgendamentoPorToken(
  token: string,
): Promise<AgendamentoAutoatendimentoResponse> {
  const resposta = await api.post<AgendamentoAutoatendimentoResponse>(
    `/public/appointments/manage/${encodeURIComponent(token)}/cancel`,
  );
  return resposta.data;
}

export async function remarcarAgendamentoPorToken(
  token: string,
  inicio: string,
): Promise<AgendamentoAutoatendimentoResponse> {
  const resposta = await api.post<AgendamentoAutoatendimentoResponse>(
    `/public/appointments/manage/${encodeURIComponent(token)}/reschedule`,
    { inicio },
  );
  return resposta.data;
}
