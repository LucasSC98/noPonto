# NoPonto

Sistema de agendamentos multi-tenant. A API fica em `noponto-api/` (Spring Boot, porta 8080) e o frontend em `frontend/` (Vite, porta 5173). O PostgreSQL sobe com `docker compose up -d`.

## Desenvolvimento local

O perfil `dev` **não** é o padrão. Ele ativa o `DevDataSeeder`, que cria o tenant `clinica-teste` e o admin `admin@teste.com` / `123456`. Sem o perfil, a aplicação sobe sem esses dados.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

O comando acima deve ser executado em `noponto-api/`. No PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
./mvnw.cmd spring-boot:run
```

O perfil `dev` traz um segredo de JWT só para a máquina local. Fora dele, a API não sobe sem `NOPONTO_JWT_SEGREDO`.

## Variáveis obrigatórias em produção

Não ative o perfil `dev`. Defina pelo menos:

| Variável | Obrigatória | Observação |
| --- | --- | --- |
| `NOPONTO_JWT_SEGREDO` | sim | Mínimo de 32 bytes. Sem valor, ou com valor menor, a API falha ao iniciar: "NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes". |
| `SPRING_DATASOURCE_URL` | sim | JDBC do PostgreSQL de produção. O padrão do repositório aponta para `localhost`. |
| `SPRING_DATASOURCE_USERNAME` | sim | Usuário do banco. |
| `SPRING_DATASOURCE_PASSWORD` | sim | Senha do banco. |
| `NOPONTO_CORS_ORIGEM_PERMITIDA` | sim | Origem do frontend. O padrão é `http://localhost:5173`. |
| `NOPONTO_DOMINIO_BASE` | não | Base do subdomínio do tenant. Padrão: `noponto.com`. |
| `NOPONTO_JWT_EXPIRACAO_MINUTOS` | não | Validade do token. Padrão: 480. |

O `docker-compose.yml` sobe apenas o PostgreSQL local. A API lê essas variáveis do ambiente do processo.
