-- Migración V11: Soporte de código OTP por correo electrónico como alternativa/complemento MFA y recuperación 2FA
ALTER TABLE mfa_challenges
    ADD COLUMN IF NOT EXISTS email_otp_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_otp_expira_en TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_otp_enviado_en TIMESTAMP;
