-- Migration inicial do sistema NoPonto
-- Todas as datas são persistidas em UTC (TIMESTAMPTZ)
-- Todas as tabelas de negócio possuem tenant_id UUID NOT NULL para isolamento multi-tenant

CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(150) NOT NULL,
    subdominio VARCHAR(63) NOT NULL UNIQUE,
    fuso_horario VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    antecedencia_minima_cancelamento_minutos INTEGER NOT NULL DEFAULT 120
        CHECK (antecedencia_minima_cancelamento_minutos >= 0),
    horario_abertura_padrao TIME NOT NULL DEFAULT '08:00:00',
    horario_fechamento_padrao TIME NOT NULL DEFAULT '18:00:00',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_tenant_horario_funcionamento CHECK (horario_abertura_padrao < horario_fechamento_padrao)
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    perfil VARCHAR(30) NOT NULL DEFAULT 'ADMIN'
        CHECK (perfil IN ('ADMIN', 'PROFISSIONAL')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usuario_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_usuario_tenant_id ON usuario (tenant_id);

CREATE TABLE profissional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(255),
    telefone VARCHAR(30),
    especialidade VARCHAR(150),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profissional_tenant_id ON profissional (tenant_id);
CREATE INDEX idx_profissional_tenant_ativo ON profissional (tenant_id, ativo);

CREATE TABLE item_servico (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    profissional_id UUID NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    nome VARCHAR(150) NOT NULL,
    descricao TEXT,
    duracao_minutos INTEGER NOT NULL CHECK (duracao_minutos > 0),
    preco NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (preco >= 0),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_item_servico_tenant_id ON item_servico (tenant_id);
CREATE INDEX idx_item_servico_tenant_profissional ON item_servico (tenant_id, profissional_id);

-- dia_semana segue o padrão ISO-8601 (java.time.DayOfWeek): 1 = segunda-feira até 7 = domingo
CREATE TABLE regra_disponibilidade (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    profissional_id UUID NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    dia_semana INTEGER NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_regra_disponibilidade_horario CHECK (hora_inicio < hora_fim)
);

CREATE INDEX idx_regra_disponibilidade_tenant_id ON regra_disponibilidade (tenant_id);
CREATE INDEX idx_regra_disponibilidade_profissional_dia
    ON regra_disponibilidade (tenant_id, profissional_id, dia_semana);

CREATE TABLE bloqueio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    profissional_id UUID NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    inicio TIMESTAMPTZ NOT NULL,
    fim TIMESTAMPTZ NOT NULL,
    motivo VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_bloqueio_periodo CHECK (inicio < fim)
);

CREATE INDEX idx_bloqueio_tenant_id ON bloqueio (tenant_id);
CREATE INDEX idx_bloqueio_profissional_periodo
    ON bloqueio (tenant_id, profissional_id, inicio, fim);

CREATE TABLE agendamento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    profissional_id UUID NOT NULL REFERENCES profissional(id),
    item_servico_id UUID NOT NULL REFERENCES item_servico(id),
    nome_cliente VARCHAR(150) NOT NULL,
    email_cliente VARCHAR(255),
    telefone_cliente VARCHAR(30),
    horario_inicio TIMESTAMPTZ NOT NULL,
    horario_fim TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'CONFIRMADO', 'CANCELADO', 'CONCLUIDO')),
    observacoes TEXT,
    cancelado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_agendamento_periodo CHECK (horario_inicio < horario_fim)
);

CREATE INDEX idx_agendamento_tenant_id ON agendamento (tenant_id);
CREATE INDEX idx_agendamento_profissional_periodo
    ON agendamento (tenant_id, profissional_id, horario_inicio, horario_fim);
CREATE INDEX idx_agendamento_tenant_status ON agendamento (tenant_id, status);
