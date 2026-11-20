export type StatusAgendamento = 'PENDENTE' | 'CONFIRMADO' | 'CANCELADO' | 'CONCLUIDO';

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEmSegundos: number;
}

export interface ProfissionalPublicoResponse {
  id: string;
  nome: string;
  especialidade: string | null;
}

export interface ServicoItemResponse {
  id: string;
  nome: string;
  duracaoMinutos: number;
  ativo?: boolean;
}

export interface CriarAgendamentoRequest {
  profissionalId: string;
  servicoItemId: string;
  nomeCliente: string;
  emailCliente: string;
  inicio: string;
}

export interface AgendamentoResponse {
  id: string;
  profissionalId: string;
  profissionalNome?: string;
  servicoItemId: string;
  servicoNome?: string;
  nomeCliente: string;
  emailCliente: string | null;
  inicio: string;
  fim: string;
  status: StatusAgendamento;
  canceladoPeloCliente?: boolean;
  remarcado?: boolean;
}

export interface AgendamentoAutoatendimentoResponse {
  id: string;
  profissionalId: string;
  profissionalNome: string;
  profissionalEspecialidade: string | null;
  servicoItemId: string;
  servicoNome: string;
  servicoDuracaoMinutos: number;
  nomeEstabelecimento: string;
  subdominioEstabelecimento: string | null;
  nomeCliente: string;
  emailCliente: string | null;
  inicio: string;
  fim: string;
  status: StatusAgendamento;
  antecedenciaMinimaCancelamentoMinutos: number;
  remarcado: boolean;
}

export interface ErroApiResponse {
  status: number;
  mensagem: string;
  momento: string;
  campos?: Record<string, string>;
}

export const TRANSICOES_STATUS: Record<StatusAgendamento, StatusAgendamento[]> = {
  PENDENTE: ['CONFIRMADO', 'CANCELADO'],
  CONFIRMADO: ['CONCLUIDO', 'CANCELADO'],
  CANCELADO: [],
  CONCLUIDO: [],
};

export const ROTULO_STATUS: Record<StatusAgendamento, string> = {
  PENDENTE: 'Pendente',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  CONCLUIDO: 'Concluído',
};
