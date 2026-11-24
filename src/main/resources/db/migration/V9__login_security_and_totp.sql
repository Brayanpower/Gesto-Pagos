-- Migración V9: Seguridad de login (bloqueo, token_version, refresh tokens, auditoría) y TOTP obligatorio por defecto

-- 1. Nuevas columnas de seguridad de login y TOTP en usuarios
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS intentos_fallidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bloqueado_hasta TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ultimo_login TIMESTAMP,
    ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS totp_obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS totp_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS totp_secret_enc VARCHAR(255),
    ADD COLUMN IF NOT EXISTS totp_configurado_en TIMESTAMP,
    ADD COLUMN IF NOT EXISTS totp_ultimo_paso BIGINT;

-- 2. Tabla de refresh tokens opacos con rotación por familia
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL,
    familia_id      VARCHAR(64)  NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,
    expira_en       TIMESTAMP    NOT NULL,
    revocado        BOOLEAN      NOT NULL DEFAULT FALSE,
    reemplazado_por VARCHAR(64),
    ip_origen       VARCHAR(64),
    user_agent      VARCHAR(255),
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario_id ON refresh_tokens (usuario_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_familia_id ON refresh_tokens (familia_id);

-- 3. Tabla de desafíos MFA temporales (enrolamiento y verificación TOTP)
CREATE TABLE IF NOT EXISTS mfa_challenges (
    id              BIGSERIAL PRIMARY KEY,
    challenge_hash  VARCHAR(64)  NOT NULL,
    usuario_id      BIGINT       NOT NULL,
    tipo            VARCHAR(20)  NOT NULL,
    intentos        INTEGER      NOT NULL DEFAULT 0,
    expira_en       TIMESTAMP    NOT NULL,
    consumido       BOOLEAN      NOT NULL DEFAULT FALSE,
    ip_origen       VARCHAR(64),
    user_agent      VARCHAR(255),
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mfa_challenges_challenge_hash UNIQUE (challenge_hash),
    CONSTRAINT fk_mfa_challenges_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mfa_challenges_usuario_id ON mfa_challenges (usuario_id);

-- 4. Tabla de auditoría de eventos de autenticación
CREATE TABLE IF NOT EXISTS auditoria_login (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT,
    correo          VARCHAR(100) NOT NULL,
    evento          VARCHAR(40)  NOT NULL,
    exitoso         BOOLEAN      NOT NULL,
    ip_origen       VARCHAR(64),
    user_agent      VARCHAR(255),
    detalle         VARCHAR(255),
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auditoria_login_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_auditoria_login_correo_fecha ON auditoria_login (correo, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_login_usuario_fecha ON auditoria_login (usuario_id, fecha_creacion DESC);

