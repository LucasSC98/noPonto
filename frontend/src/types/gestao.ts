export interface ServicoVinculadoResponse {
  id: string;
  nome: string;
  duracaoMinutos: number;
}

export interface ProfissionalResponse {
  id: string;
  nome: string;
  email: string | null;
  ativo: boolean;
  temDisponibilidade: boolean;
  quantidadeServicosAtivos: number;
  agendamentosSemana: number;
  servicosAtivos: ServicoVinculadoResponse[];
}

export interface CriarProfissionalRequest {
  nome: string;
  email?: string;
}

export interface AtualizarProfissionalRequest {
  nome: string;
  email?: string;
  ativo: boolean;
}

export interface ServicoItemResponse {
  id: string;
  nome: string;
  duracaoMinutos: number;
  ativo: boolean;
}

export interface CriarServicoItemRequest {
  profissionalId: string;
  nome: string;
  duracaoMinutos: number;
}

export interface AtualizarServicoItemRequest {
  nome: string;
  duracaoMinutos: number;
  ativo: boolean;
}

export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface RegraDisponibilidadeResponse {
  id: string;
  profissionalId: string;
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFim: string;
  ativo: boolean;
}

export interface SalvarRegraDisponibilidadeRequest {
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFim: string;
}

export interface CriarRegraDisponibilidadeRequest extends SalvarRegraDisponibilidadeRequest {
  profissionalId: string;
}

export interface AtualizarRegraDisponibilidadeRequest extends SalvarRegraDisponibilidadeRequest {
  ativo: boolean;
}

export interface TenantAtualResponse {
  subdominio: string;
}

export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
