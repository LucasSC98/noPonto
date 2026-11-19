-- Garante as colunas opcionais especialidade (máx. 80) e telefone (máx. 20) em profissional
-- sem alterar migrations anteriores.

ALTER TABLE profissional
    ADD COLUMN IF NOT EXISTS especialidade VARCHAR(80),
    ADD COLUMN IF NOT EXISTS telefone VARCHAR(20);

ALTER TABLE profissional
    ALTER COLUMN especialidade TYPE VARCHAR(80),
    ALTER COLUMN telefone TYPE VARCHAR(20);
