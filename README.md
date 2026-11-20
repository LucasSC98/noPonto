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
