-- Migración V8: Onboarding con cuenta única de ahorro, estatus de revisión por admin y desacoplamiento de usuarios ROLE_ADMIN

-- 1. Asegurar columna tipo_cuenta = 'AHORRO' y columnas de auditoría de revisión en cuentas
ALTER TABLE cuentas
    ADD COLUMN IF NOT EXISTS tipo_cuenta VARCHAR(20) NOT NULL DEFAULT 'AHORRO';

ALTER TABLE cuentas
    ALTER COLUMN estatus TYPE VARCHAR(30);

ALTER TABLE cuentas
    ADD COLUMN IF NOT EXISTS revisado_por VARCHAR(100);

ALTER TABLE cuentas
    ADD COLUMN IF NOT EXISTS observacion_revision VARCHAR(255);

ALTER TABLE cuentas
    ADD COLUMN IF NOT EXISTS fecha_revision TIMESTAMP;

-- Eliminar posibles cuentas duplicadas sin transacciones en ambientes previos de prueba antes de imponer 1:1
DELETE FROM cuentas c
WHERE c.id NOT IN (
    SELECT MIN(c2.id)
    FROM cuentas c2
    GROUP BY c2.cliente_id
)
AND NOT EXISTS (
    SELECT 1
    FROM transacciones_servicio t
    WHERE t.cuenta_id = c.id
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cuentas_tipo') THEN
        ALTER TABLE cuentas
            ADD CONSTRAINT chk_cuentas_tipo CHECK (tipo_cuenta = 'AHORRO');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_cuentas_cliente') THEN
        IF NOT EXISTS (
            SELECT cliente_id FROM cuentas GROUP BY cliente_id HAVING COUNT(*) > 1
        ) THEN
            ALTER TABLE cuentas
                ADD CONSTRAINT uq_cuentas_cliente UNIQUE (cliente_id);
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_cuentas_tipo_cuenta ON cuentas (tipo_cuenta);

-- 2. Permitir usuarios ROLE_ADMIN puros sin perfil de cliente ni cuenta de ahorro
ALTER TABLE usuarios
    ALTER COLUMN cliente_id DROP NOT NULL;

