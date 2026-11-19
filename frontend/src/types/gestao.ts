export interface ProfissionalResponse {
  id: string;
  nome: string;
  email: string | null;
  ativo: boolean;
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

export interface CriarRegraDisponibilidadeRequest {
  profissionalId: string;
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFim: string;
}

export interface AtualizarRegraDisponibilidadeRequest {
  diaSemana: DiaSemana;
  horaInicio: string;
  horaFim: string;
  ativo: boolean;
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
