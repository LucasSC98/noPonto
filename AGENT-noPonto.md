# AGENT.md — NoPonto

Nov 18, 2026 · @Lucas

## Visão Geral

Este arquivo define as regras que o agente deve seguir em **todas** as interações com o projeto NoPonto. Ele é a fonte de verdade para convenções de código, fluxo de git, regras de negócio e qualidade. Sempre que houver dúvida sobre como proceder, consulte este arquivo antes de tomar uma decisão.

## Idioma e Nomenclatura

Todo o código de domínio deve ser escrito em **português do Brasil**. Isso inclui nomes de variáveis, métodos, classes, enums, constantes e comentários.

### O que fica em inglês

- Palavras reservadas e anotações do framework (`@Entity`, `@Service`, `@GetMapping`, `useState`, etc.)
- Nomes de bibliotecas e dependências externas
- Palavras da infraestrutura (`dto`, `repository`, `controller`, `service`, `config`)
- Mensagens de log técnico e exceções de sistema

### Regras de nomenclatura

- **Classes/Interfaces**: PascalCase em português — `Agendamento`, `ServicoItem`, `RegraDisponibilidade`
- **Métodos e variáveis**: camelCase em português — `calcularSlots()`, `tenantId`, `horaInicio`
- **Constantes**: UPPER\_SNAKE\_CASE — `DURACAO_PADRAO_MINUTOS`, `LIMITE_AGENDAMENTOS_DIA`
- **Tabelas no banco**: snake\_case em português — `agendamento`, `regra_disponibilidade`, `item_servico`
- **Endpoints REST**: kebab-case em inglês, seguindo convenções REST padrão — `/appointments`, `/available-slots`, `/availability-rules`
- **Branches Git**: kebab-case em português — `feat/calculo-slots`, `fix/conflito-agendamento`

### DTOs

Sempre usar `record` do Java para DTOs. Exemplo:

```java
public record CriarAgendamentoRequest(String nomeCliente, OffsetDateTime horario, Long servicoId) {}
```

## Fluxo de Git e Commits

### Regra principal

Após **qualquer** alteração funcional — nova feature, correção de bug, refatoração com impacto comportamental ou adição de migration — o agente deve fazer um commit antes de prosseguir para a próxima tarefa.

### Formato de mensagem (Conventional Commits em português)

```
<tipo>(<escopo>): <descrição curta em português>
```

**Tipos permitidos:**

- `feat` — nova funcionalidade
- `fix` — correção de bug
- `refactor` — refatoração sem mudança de comportamento
- `test` — adição ou ajuste de testes
- `chore` — configuração, dependências, CI
- `docs` — documentação
- `db` — criação ou alteração de migration Flyway

**Exemplos de mensagens válidas:**

```
feat(agendamento): implementar cálculo de slots disponíveis
fix(tenant): corrigir vazamento de dados entre tenants no filtro
db(flyway): adicionar tabela regra_disponibilidade
test(slots): cobrir cenário de sobreposição de bloqueios
```

### Branches

- `main` — código estável, nunca commitar diretamente
- `develop` — branch de integração
- `feat/<nome>` — novas funcionalidades
- `fix/<nome>` — correções

### Checklist antes de cada commit

- [ ] O código compila sem erros
- [ ] Os testes existentes continuam passando
- [ ] Nenhum `System.out.println` ou `console.log` de debug foi deixado
- [ ] A mensagem do commit descreve claramente o que foi feito

## Arquitetura e Regras de Código

### Estrutura de pacotes (backend)

```
me.lucascustodio.noponto
├── config/          # Spring Security, JWT, CORS, beans globais
├── domain/          # Entidades JPA e enums de negócio
├── repository/      # Interfaces Spring Data JPA
├── service/         # Regras de negócio (sem lógica nos controllers)
├── controller/      # Endpoints REST, apenas delegam ao serviço
├── dto/             # Records de entrada e saída
├── exception/       # Exceções de domínio e handler global
└── tenant/          # Filtro, contexto e resolução do tenant_id
```

### Regras de camadas

- **Controllers** não contêm lógica de negócio — apenas recebem a requisição, delegam ao serviço e retornam a resposta
- **Serviços** são a única camada que toma decisões de negócio
- **Repositórios** não recebem lógica além de queries
- Nunca injetar um `Repositorio` diretamente em outro `Servico` — sempre passar pelo serviço responsável pelo domínio

### Multi-tenancy

- Toda tabela de negócio possui coluna `tenant_id NOT NULL`
- O `tenant_id` é resolvido do JWT no filtro e injetado no contexto via `TenantContextHolder` (ThreadLocal)
- Toda query no repositório filtra por `tenantId` — nunca buscar sem esse filtro
- Nunca expor o `tenant_id` em respostas da API pública

### Datas e horários

- Sempre usar `OffsetDateTime` (nunca `LocalDateTime`)
- Persistir e retornar todas as datas em **UTC**
- A conversão para o fuso do estabelecimento é responsabilidade do frontend

### Exceções

- Exceções de domínio estendem uma classe base `ExcecaoDominio`
- Erros esperados (não encontrado, conflito, sem permissão) lançam exceções com mensagens em português
- O handler global (`@RestControllerAdvice`) mapeia exceções para status HTTP adequado

### Segurança

- Nenhum endpoint sensível sem autenticação JWT
- Fluxo público de agendamento (consulta de slots + criação) é o único conjunto de rotas liberado sem token
- Validar sempre o `tenant_id` do JWT contra o recurso acessado antes de qualquer operação

## Regras de Negócio do Domínio

### Agendamento

- Um agendamento só pode ser criado em um horário calculado como disponível pelo motor de slots
- Dois clientes **não podem** reservar o mesmo profissional no mesmo horário — usar `SELECT FOR UPDATE` (pessimistic lock) na transação de criação
- O agendamento ocupa o intervalo `[horario_inicio, horario_inicio + duracao_servico)`
- Status possíveis do agendamento: `PENDENTE`, `CONFIRMADO`, `CANCELADO`, `CONCLUIDO`
- Cancelamento só é permitido com antecedência mínima configurável por tenant (padrão: 2 horas antes)
- Após `CONCLUIDO`, o agendamento é imutável

### Slots disponíveis

- Slots são calculados em memória — nunca salvar horários vazios no banco
- O cálculo cruza três fontes:
  1. `RegraDisponibilidade` do profissional (dias e horários de atendimento)
  2. `Bloqueio` (folgas, feriados, indisponibilidades pontuais)
  3. `Agendamento` existentes com status `PENDENTE` ou `CONFIRMADO`
- A duração do slot é a duração do `ServicoItem` solicitado
- Slots parcialmente sobrepostos a um bloqueio ou agendamento existente são descartados
- O endpoint de slots disponíveis é público (sem autenticação) mas exige `tenant_id` via path ou subdomínio

### Profissional e Serviços

- Um profissional pertence a exatamente um tenant
- Um profissional pode oferecer múltiplos `ServicoItem`
- Cada `ServicoItem` tem duração em minutos — usada no cálculo de slots
- Um profissional inativo (`ativo = false`) não aparece na listagem pública e não aceita novos agendamentos

### Tenant

- Cada tenant tem seu próprio subdomínio (ex: `clinica.noponto.com`)
- O `tenant_id` é resolvido do subdomínio na requisição pública e do JWT nas requisições autenticadas
- Um tenant pode configurar: antecedência mínima de cancelamento, horário de funcionamento padrão e fuso horário de exibição

### Usuários

- Usuários administradores gerenciam o tenant (profissionais, serviços, bloqueios)
- Um usuário pertence a exatamente um tenant e não pode visualizar dados de outro

## Testes e Qualidade

### Obrigatoriedade

- O motor de cálculo de slots (`ServicoAgendamento`) deve ter cobertura de testes unitários com JUnit 5
- Qualquer lógica que envolva datas, horários ou sobreposições deve ter testes antes do commit
- Correções de bug devem vir acompanhadas de um teste que reproduza o cenário corrigido

### Nomenclatura dos testes

Usar o padrão `deve_<comportamento>_quando_<cenário>`:

```java
@Test
void deve_retornarSlotVazio_quando_profissionalEstaBloqueado() { … }

@Test
void deve_lancarExcecao_quando_horarioJaReservado() { … }
```

### Cenários obrigatórios para o motor de slots

- [ ] Profissional sem regra de disponibilidade → retorna lista vazia
- [ ] Horário dentro de um bloqueio → slot descartado
- [ ] Agendamento existente ocupa o slot → slot descartado
- [ ] Slot parcialmente sobreposto a agendamento → slot descartado
- [ ] Dois agendamentos simultâneos (concorrência) → apenas um confirmado
- [ ] Duração do serviço maior que a janela disponível → slot não gerado

### Ferramentas

- **Backend**: JUnit 5 + Mockito para unitários; `@SpringBootTest` + Testcontainers para integração com banco real
- **Frontend**: Vitest para lógica; nenhum teste de snapshot por padrão

### O que não fazer

- Não commitar código com testes falhando
- Não usar `@Disabled` para suprimir falhas — corrigir o código ou o teste
- Não mockar o banco em testes de integração — usar Testcontainers com PostgreSQL real
