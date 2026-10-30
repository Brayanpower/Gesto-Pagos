-- Migración V7: Tabla de transacciones de pago de servicios y recargas (GestoPago)

CREATE TABLE IF NOT EXISTS transacciones_servicio (
    id                          BIGSERIAL PRIMARY KEY,
    cuenta_id                   BIGINT         NOT NULL,
    cliente_id                  BIGINT         NOT NULL,
    numero_cuenta               VARCHAR(20)    NOT NULL,
    clave_idempotencia          VARCHAR(100)   NOT NULL,
    upc                         VARCHAR(15)    NOT NULL,
    id_servicio                 INTEGER        NOT NULL,
    nombre_servicio             VARCHAR(150),
    id_producto                 INTEGER        NOT NULL,
    nombre_producto             VARCHAR(150),
    monto                       NUMERIC(15, 2) NOT NULL,
    saldo_anterior              NUMERIC(15, 2) NOT NULL,
    saldo_posterior             NUMERIC(15, 2) NOT NULL,
    telefono                    VARCHAR(20),
    referencia                  VARCHAR(100),
    estatus                     VARCHAR(30)    NOT NULL,
    codigo_respuesta_proveedor  VARCHAR(20),
    mensaje_proveedor           VARCHAR(500),
    folio_proveedor             VARCHAR(100),
    num_autorizacion            VARCHAR(100),
    hora_local                  VARCHAR(14)    NOT NULL,
    intentos_conciliacion       INTEGER        NOT NULL DEFAULT 0,
    fecha_creacion              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion         TIMESTAMP,
    CONSTRAINT uq_transacciones_idempotencia UNIQUE (clave_idempotencia),
    CONSTRAINT uq_transacciones_upc UNIQUE (upc),
    CONSTRAINT chk_transacciones_monto_positivo CHECK (monto > 0),
    CONSTRAINT fk_transacciones_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transacciones_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_tx_servicio_cliente_fecha ON transacciones_servicio (cliente_id, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_tx_servicio_cuenta_fecha ON transacciones_servicio (cuenta_id, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_tx_servicio_numero_cuenta ON transacciones_servicio (numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_tx_servicio_estatus ON transacciones_servicio (estatus);

