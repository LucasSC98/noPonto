import { z } from 'zod';
import { dataNoPassado } from './datas';

export const esquemaLogin = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Informe seu e-mail.')
    .email('Informe um e-mail válido.'),
  senha: z.string().min(1, 'Informe sua senha.'),
});

export type DadosLogin = z.infer<typeof esquemaLogin>;

export const esquemaAgendamentoPublico = z.object({
  data: z
    .string()
    .min(1, 'Selecione uma data.')
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida.')
    .refine((valor) => !dataNoPassado(valor), {
      message: 'A data não pode ser no passado.',
    }),
  servicoItemId: z
    .string()
    .trim()
    .min(1, 'Informe o serviço desejado.')
    .uuid('Identificador de serviço inválido.'),
  slot: z.string().min(1, 'Selecione um horário disponível.'),
  nomeCliente: z
    .string()
    .trim()
    .min(2, 'Informe seu nome completo.')
    .max(150, 'O nome deve ter no máximo 150 caracteres.'),
  emailCliente: z
    .string()
    .trim()
    .min(1, 'Informe seu e-mail.')
    .email('Informe um e-mail válido.')
    .max(255, 'O e-mail deve ter no máximo 255 caracteres.'),
});

export type DadosAgendamentoPublico = z.infer<typeof esquemaAgendamentoPublico>;
