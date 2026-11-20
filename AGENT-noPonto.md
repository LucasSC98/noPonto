# AGENT.md — NoPonto

Nov 20, 2026 · @Lucas

## Visão Geral

Este arquivo define as regras que o agente deve seguir em **todas** as interações com o projeto NoPonto. Ele é a fonte de verdade para arquitetura, convenções de código, fluxo de git, regras de negócio, testes e qualidade. Sempre que houver dúvida sobre como proceder, consulte este arquivo e o código existente antes de tomar uma decisão.

## Estrutura do Repositório e Stack

```
noPonto/
├── docker-compose.yml   # PostgreSQL 16 (banco noponto_db, usuário/senha noponto, TZ UTC)
├── noponto-api/         # Backend Spring Boot (Java 21, porta 8080)
└── frontend/            # Frontend React + TypeScript (Vite, porta 5173)
```

- **Backend (`noponto-api/`)**: Java 21, Spring Boot `4.1.2` (`spring-boot-starter-webmvc`, Data JPA, Security, Validation, Flyway, Lombok, JJWT `0.13.0`), PostgreSQL 16.
- **Frontend (`frontend/`)**: Vite 8, React 19, TypeScript (`verbatimModuleSyntax` e `erasableSyntaxOnly` ativos — usar `import type` para tipos e `type` unions em vez de `enum` TS), React Router 7, React Hook Form + Zod (`@hookform/resolvers`), Axios, `date-fns`, CSS Modules (sem biblioteca de componentes externa), `oxlint` e Vitest.
- **Ambiente Windows**: se a política de execução do PowerShell bloquear `npm.ps1`, executar sempre via `npm.cmd` / `npx.cmd`.
- **Seed de desenvolvimento (`DevDataSeeder`)**: `@Profile("dev")`, **não** ativo por padrão. Subir localmente com `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`. Idempotente (`tenantRepository.count() > 0`). Popula o tenant `clinica-teste`, usuário admin `admin@teste.com` / `123456`, profissional `Dr. João Silva` (`Clínico Geral`), serviços `Consulta Geral` (30 min) e `Retorno` (15 min), e regras Seg–Sex `09:00–18:00`.

## Idioma e Nomenclatura

Todo o código de domínio deve ser escrito em **português do Brasil**. Isso inclui nomes de variáveis, métodos, classes, enums, constantes, DTOs e comentários.

### O que fica em inglês

- Palavras reservadas e anotações do framework (`@Entity`, `@Service`, `@GetMapping`, `useState`, etc.)
- Nomes de bibliotecas e dependências externas
- Termos da infraestrutura (`dto`, `repository`, `controller`, `service`, `config`, `integration`, `routes`, `components`, `pages`, `utils`)
- Mensagens de log técnico e exceções de sistema

### Regras de nomenclatura

- **Classes/Interfaces**: PascalCase em português — `Agendamento`, `ServicoItem`, `RegraDisponibilidade`, `ExcecaoDominio`
- **Métodos e variáveis**: camelCase em português — `calcularSlotsDisponiveis()`, `tenantId`, `horaInicio`
- **Constantes**: UPPER\_SNAKE\_CASE — `ANTECEDENCIA_MINIMA_CANCELAMENTO_PADRAO_MINUTOS`, `TIMEOUT_SEGUNDOS`
- **Tabelas no banco**: snake\_case em português — `tenant`, `usuario`, `profissional`, `item_servico`, `regra_disponibilidade`, `bloqueio`, `agendamento`
- **Endpoints REST**: novos endpoints seguem inglês em kebab-case (`/professionals/{id}/status`, `/professionals/{id}/availability-rules`, `/services/{id}/status`, `/tenants/current`), mantendo compatibilidade com os endpoints existentes (`/profissionais`, `/servicos`, `/regras-disponibilidade`, `/agendamentos`, `/public/**`, `/auth/login`)
- **Branches Git**: kebab-case em português — `feat/calculo-slots`, `fix/conflito-agendamento`

### DTOs

Sempre usar `record` do Java para DTOs. Exemplo:

```java
public record CriarAgendamentoRequest(
        @NotNull UUID profissionalId,
        @NotNull UUID servicoItemId,
        @NotBlank @Size(max = 150) String nomeCliente,
        @NotBlank @Email @Size(max = 255) String emailCliente,
        @NotNull OffsetDateTime inicio,
        String tenant) {}
```

## Fluxo de Git e Commits

### Regra principal

Após **qualquer** alteração funcional — nova feature, correção de bug, refatoração com impacto comportamental ou adição de migration — o agente deve validar tudo e fazer um commit antes de prosseguir para a próxima tarefa. Fazer stage apenas dos arquivos relacionados à tarefa, sem misturar alterações prévias não relacionadas do working tree.

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
feat(agendamento): motor de slots + endpoint transacional com lock
fix(tenant): resolver tenant por query param nas rotas públicas
db(flyway): adicionar especialidade e telefone ao profissional
test(agendamento): teste de concorrência contra double booking
```

### Branches

- `main` — código estável, nunca commitar diretamente após o setup inicial
- `develop` — branch de integração ativa
- `feat/<nome>` — novas funcionalidades
- `fix/<nome>` — correções

### Checklist antes de cada commit

- [ ] Backend: `./mvnw test` passa sem erros (e `./mvnw verify` ao alterar concorrência, JPA, migrations ou fluxos de integração)
- [ ] Frontend: `npm run build`, `npm run lint` e `npm test` passam sem erros
- [ ] Nenhum `System.out.println`, `printStackTrace` ou `console.log` de debug foi deixado (usar `@Slf4j` no backend)
- [ ] Apenas os arquivos da tarefa foram adicionados ao commit e a mensagem segue Conventional Commits

## Arquitetura e Regras de Código (Backend)

### Estrutura de pacotes (`noponto-api`)

```
me.lucascustodio.noponto
├── config/          # Spring Security, CORS, Clock UTC, DevDataSeeder
├── domain/          # Entidades JPA (EntidadeBase), conversores, enums de negócio
├── repository/      # Interfaces Spring Data Repository e projeções de consulta
├── service/         # Regras de negócio (sem lógica nos controllers)
├── controller/      # Endpoints REST, apenas resolvem entrada/tenant e delegam
├── dto/             # Records de entrada e saída
├── exception/       # ExcecaoDominio, subclasses e TratadorGlobalExcecoes
└── tenant/          # JwtAuthFilter, TenantContextHolder, ResolvedorTenantPublico
```

### Regras de camadas e repositórios

- **Controllers** não contêm lógica de negócio — apenas validam a entrada (`@Valid`), obtêm o `tenantId` (`TenantContextHolder.get()` ou `ResolvedorTenantPublico`) e delegam ao serviço.
- **Serviços** são a única camada que toma decisões de negócio.
- **Repositórios** de negócio devem estender `org.springframework.data.repository.Repository<T, UUID>` (evitar `JpaRepository`) para não expor métodos como `findById`/`findAll` sem filtro de `tenantId`. A única exceção sem filtro de `tenantId` é a busca de login em `UsuarioRepository`.
- Nunca injetar um `Repository` de outro domínio diretamente em um `Service` — sempre passar pelo serviço responsável pelo domínio (ex.: `GestaoProfissionalService` consulta `GestaoRegraDisponibilidadeService`, `GestaoServicoItemService` e `AgendamentoConsultaService`).
- **Evitar N+1** em listagens usando queries em lote com `IN (:profissionalIds)`, `GROUP BY` ou projeções DTO JPQL (`ContagemPorProfissional`, `ServicoVinculadoPorProfissional`).

### Multi-tenancy

- Toda tabela de negócio possui PK `UUID DEFAULT gen_random_uuid()` e coluna `tenant_id UUID NOT NULL REFERENCES tenant(id)` (via `EntidadeBase`).
- **Rotas autenticadas**: `JwtAuthFilter` extrai o Bearer JWT (claims `sub`, `tenantId`, `role`), popula o `SecurityContext` e injeta o `tenant_id` no `TenantContextHolder` (`ThreadLocal`), sempre limpando no bloco `finally`.
- **Rotas públicas (`/public/**`)**: `ResolvedorTenantPublico` resolve o tenant na ordem **subdomínio do host (`<tenant>.noponto.com`) → query param `tenant` → header `X-Tenant-Subdominio` → campo `tenant` no body**, e popula `TenantContextHolder.set(tenantId)` antes de qualquer consulta.
- Toda query de negócio filtra explicitamente por `tenantId`.
- Nunca expor `tenant_id`, `email` ou `telefone` do profissional em respostas da API pública (`ProfissionalPublicoResponse` expõe apenas `id`, `nome`, `especialidade`).

### Datas e horários

- Datas/instantes sempre em `OffsetDateTime` + `TIMESTAMPTZ` (UTC). Nunca usar `LocalDateTime`.
- Janelas semanais (`RegraDisponibilidade`) usam `OffsetTime` + `TIMETZ` com `@JdbcType(TipoHorarioComFuso.class)` para preservar o relógio e o offset originais, e `dia_semana` persistido como `INTEGER` ISO-8601 (`1 = MONDAY` .. `7 = SUNDAY` via `ConversorDiaSemana`).
- Injetar `Clock` (`RelogioConfig`, UTC) em serviços dependentes do horário atual (`SlotService`, `AgendamentoService`, `AgendamentoConsultaService`, `JwtService`) para permitir testes determinísticos.
- "Esta semana" nas métricas de profissionais corresponde a segunda-feira `00:00Z` até a segunda-feira seguinte `00:00Z`.
- A conversão para exibição no fuso local é responsabilidade do frontend.

### Banco de dados e Flyway

- `spring.jpa.hibernate.ddl-auto: validate` — quem cria e altera o schema é exclusivamente o Flyway (`classpath:db/migration`).
- **Nunca** alterar migrations já aplicadas (`V1` a `V4`). Toda mudança de schema deve ir em nova migration (`V5__...`) com commit `db(flyway): ...` separado.

### Exceções e Segurança

- Exceções de domínio estendem `ExcecaoDominio`: `CredenciaisInvalidasExcecao` (401), `RecursoNaoEncontradoExcecao` (404), `HorarioIndisponivelExcecao` (409), `RegraNegocioExcecao` (422), `TenantNaoIdentificadoExcecao` (400).
- `TratadorGlobalExcecoes` (`@RestControllerAdvice`) mapeia `ExcecaoDominio` e `MethodArgumentNotValidException` para `ErroResponse` com mensagens em português.
- API stateless (`SessionCreationPolicy.STATELESS`, CSRF desabilitado) com `JwtAuthFilter` antes de `UsernamePasswordAuthenticationFilter` (e `FilterRegistrationBean.setEnabled(false)` para não rodar fora da cadeia de segurança).
- `noponto.jwt.segredo` não tem valor padrão no `application.yml`. Fora dos perfis `dev` e `test`, vem só de `NOPONTO_JWT_SEGREDO`. Segredo ausente ou com menos de 32 bytes impede a subida (`JwtService`).
- Rotas públicas liberadas sem JWT: `POST /auth/login`, `GET /public/**`, `POST /public/agendamentos`, `OPTIONS /**` e `/error`. Tudo o mais exige JWT válido.
- CORS configurado em `SecurityConfig` via propriedade `noponto.cors.origem-permitida` (`application.yml`), permitindo preflight `OPTIONS` antes do filtro JWT.
- Validar sempre o `tenant_id` do JWT contra o recurso acessado antes de qualquer operação.

## Arquitetura e Convenções (Frontend)

- Estrutura em `frontend/src/`: `pages/`, `components/`, `routes/PrivateRoute.tsx`, `services/`, `types/`, `utils/`.
- `src/services/api.ts` aponta para `VITE_API_URL || http://localhost:8080`, injeta `Authorization: Bearer <token>` do `localStorage` (`noponto_token`) e inclui `?tenant=<TENANT_PUBLICO_PADRAO>` automaticamente nas chamadas `/public/**` quando não houver `tenant` informado.
- Rotas administrativas (`/dashboard`, `/profissionais`, `/profissionais/:profissionalId/disponibilidade`, `/servicos`) protegidas por `PrivateRoute` e redirecionamento para `/login` em respostas `401`.
- Estilização com **CSS Modules** e variáveis CSS em `src/index.css` — sem biblioteca de componentes externa; reaproveitar estilos existentes de card, badge, pílula e botão.
- Formulários usam `noValidate` com mensagens de erro inline (`.entradaErro` / `.mensagemErro`), sem depender da validação nativa do navegador.
- Menus suspensos/ações secundárias (`MenuAcoes`) devem ser implementados sem libs externas, acessíveis por teclado (`aria-haspopup="menu"`, `aria-expanded`, `role="menu"`, `ArrowUp`/`ArrowDown`, `Esc` e clique fora).
- Proteger renderizações que dependam de listas ou campos opcionais da API com fallbacks defensivos (`?? []`, `?? 0`).

## Regras de Negócio do Domínio

### Agendamento

- Um agendamento só pode ser criado em um horário calculado como disponível pelo motor de slots (`SlotService.slotDisponivel(...)`).
- Dois clientes **não podem** reservar o mesmo profissional em horários conflitantes: `AgendamentoService.criar(...)` adquire lock pessimista (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT FOR UPDATE`) na linha do **`Profissional`** (`buscarAtivoComBloqueio`) antes de recalcular a disponibilidade e salvar na mesma transação `@Transactional` — isso serializa reservas simultâneas do mesmo profissional mesmo quando ainda não existe nenhum agendamento no horário.
- O agendamento ocupa o intervalo semiaberto `[inicio, fim)`, onde `fim = inicio + duracaoMinutos` do `ServicoItem`.
- Status possíveis: `PENDENTE`, `CONFIRMADO`, `CANCELADO`, `CONCLUIDO`.
- Transições permitidas: `PENDENTE → CONFIRMADO | CANCELADO`; `CONFIRMADO → CONCLUIDO | CANCELADO`. Após `CONCLUIDO` ou `CANCELADO`, o agendamento é imutável.
- Cancelamento só é permitido respeitando a antecedência mínima configurável por tenant (padrão: `120` minutos antes do início).

### Slots disponíveis (`SlotService`)

- Slots são calculados em memória — nunca salvar horários vazios no banco.
- O cálculo cruza três fontes:
  1. `RegraDisponibilidade` ativa do profissional para o dia da semana
  2. `Bloqueio` que intercepta o intervalo
  3. `Agendamento` existentes com status `PENDENTE` ou `CONFIRMADO`
- Os slots são alinhados à grade da duração do `ServicoItem` solicitado a partir do início da janela da regra.
- Slots no passado ou parcialmente sobrepostos a bloqueios ou agendamentos existentes são descartados.

### Profissional e Serviços

- Um profissional pertence a exatamente um tenant e pode oferecer múltiplos `ServicoItem` (cada um com `duracaoMinutos`).
- Campos opcionais em `Profissional`: `especialidade` (máx. 80) e `telefone` (máx. 20). Na página pública de agendamento exibe `"Nome · Especialidade"` quando presente e **nunca** expõe `telefone` ou `email`.
- Profissionais e serviços usam exclusão lógica (`ativo = false`) para preservar o histórico; inativos não aparecem na listagem pública e recusam novos agendamentos (`RegraNegocioExcecao`, 422).
- Na gestão de profissionais, o alerta `"Sem horários configurados"` aparece apenas para profissionais **ativos** sem regras de disponibilidade (ocultado para inativos, pois não entram na agenda pública).

### Tenant e Usuários

- Cada tenant possui `subdominio` único (`clinica.noponto.com`), antecedência mínima de cancelamento, horário padrão de funcionamento e fuso de exibição.
- `Usuario` possui `role` (`PerfilUsuario`: `ADMIN`, `PROFISSIONAL`) mapeado na coluna `perfil`, senha hasheada com BCrypt e unicidade `(tenant_id, email)`.
- Um usuário pertence a exatamente um tenant e nunca acessa dados de outro.

## Testes e Qualidade

### Obrigatoriedade

- O motor de cálculo de slots (`SlotService`) e a criação transacional (`AgendamentoService`) devem manter cobertura de testes unitários com JUnit 5 + Mockito.
- Qualquer lógica envolvendo datas, horários, sobreposições, concorrência ou regras de negócio deve ter testes antes do commit.
- Correções de bug devem vir acompanhadas de um teste que reproduza o cenário corrigido.

### Nomenclatura dos testes

Usar o padrão `deve_<comportamento>_quando_<cenário>`:

```java
@Test
void deve_retornarListaVazia_quando_profissionalSemRegraDeDisponibilidade() { … }

@Test
void deve_lancarExcecao_quando_horarioJaReservado() { … }
```

### Cenários obrigatórios para o motor de slots e concorrência

- [x] Profissional sem regra de disponibilidade → retorna lista vazia (`SlotServiceTest`)
- [x] Horário dentro de um bloqueio → slot descartado (`SlotServiceTest`)
- [x] Agendamento existente ocupa o slot → slot descartado (`SlotServiceTest`)
- [x] Slot parcialmente sobreposto a agendamento → slot descartado (`SlotServiceTest`)
- [x] Dois agendamentos simultâneos e horários sobrepostos (concorrência) → apenas um confirmado (`AgendamentoConcorrenciaIT`)
- [x] Duração do serviço maior que a janela disponível → slot não gerado (`SlotServiceTest`)

### Ferramentas e Execução

- **Backend (Maven Surefire vs Failsafe)**:
  - `./mvnw test` executa testes unitários e testes rápidos (`*Test` / `*Tests`).
  - `./mvnw verify` executa Surefire + testes de integração `*IT` via `maven-failsafe-plugin` (ex.: `AgendamentoConcorrenciaIT`).
- **Testcontainers (`2.0.5`, gerenciado pelo Spring Boot `4.1.2`)**:
  - Dependências `test`: `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`; classe `org.testcontainers.postgresql.PostgreSQLContainer`.
  - Anotar testes com `@Testcontainers(disabledWithoutDocker = true)` e `@ServiceConnection` para rodar contra PostgreSQL 16 real sem quebrar builds quando o Docker estiver parado.
  - Em testes de concorrência (`*IT`), **não** usar `@Transactional` na classe de teste; sincronizar threads com `ExecutorService` + `CountDownLatch` + timeout explícito (`30s`).
  - Em testes de integração `@SpringBootTest` + `@Transactional` (ex.: `ResumoProfissionalIntegracaoTest`), chamar `entityManager.flush(); entityManager.clear();` antes das consultas para validar queries reais no banco sem depender do cache L1 do Hibernate.
- **Frontend**: Vitest (`npm test`) para regras de validação e datas; sem testes de snapshot por padrão.

### O que não fazer

- Não commitar código com testes falhando, lint com erros ou build quebrado.
- Não usar `@Disabled` para suprimir falhas — corrigir o código ou o teste.
- Não mockar o banco em testes de integração — usar PostgreSQL real (Docker Compose / Testcontainers).
