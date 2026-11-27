-- Migración V10: Códigos de recuperación de un solo uso para autenticación multifactor (2FA TOTP)

CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT      NOT NULL,
    code_hash       VARCHAR(64) NOT NULL,
    usado           BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_creacion  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_uso       TIMESTAMP,
    CONSTRAINT uq_mfa_recovery_usuario_hash UNIQUE (usuario_id, code_hash),
    CONSTRAINT fk_mfa_recovery_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_usuario_id ON mfa_recovery_codes (usuario_id);
