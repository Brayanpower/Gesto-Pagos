-- Migración V4: Esquema Core Bancario (Clientes, Domicilios, Cuentas, Usuarios)

CREATE TABLE IF NOT EXISTS clientes (
    id                   BIGSERIAL PRIMARY KEY,
    nombre               VARCHAR(50)  NOT NULL,
    segundo_nombre       VARCHAR(50),
    apellido_paterno     VARCHAR(50)  NOT NULL,
    apellido_materno     VARCHAR(50)  NOT NULL,
    fecha_nacimiento     DATE         NOT NULL,
    curp                 VARCHAR(18)  NOT NULL,
    rfc                  VARCHAR(13)  NOT NULL,
    genero_id            BIGINT       NOT NULL,
    nacionalidad_id      BIGINT       NOT NULL,
    estado_civil_id      BIGINT       NOT NULL,
    correo               VARCHAR(100) NOT NULL,
    telefono_movil       VARCHAR(10)  NOT NULL,
    telefono_alternativo VARCHAR(10),
    ocupacion            VARCHAR(100) NOT NULL,
    empresa              VARCHAR(250) NOT NULL,
    ingreso_mensual      NUMERIC(15, 2) NOT NULL,
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT uq_clientes_curp UNIQUE (curp),
    CONSTRAINT uq_clientes_rfc UNIQUE (rfc),
    CONSTRAINT uq_clientes_correo UNIQUE (correo),
    CONSTRAINT fk_clientes_genero FOREIGN KEY (genero_id) REFERENCES catalogoGenero (id),
    CONSTRAINT fk_clientes_nacionalidad FOREIGN KEY (nacionalidad_id) REFERENCES catalogoNacionalidad (id),
    CONSTRAINT fk_clientes_estado_civil FOREIGN KEY (estado_civil_id) REFERENCES catalogoEstadoCivil (id)
);

CREATE INDEX IF NOT EXISTS idx_clientes_curp ON clientes (curp);
CREATE INDEX IF NOT EXISTS idx_clientes_rfc ON clientes (rfc);
CREATE INDEX IF NOT EXISTS idx_clientes_correo ON clientes (correo);
CREATE INDEX IF NOT EXISTS idx_clientes_activo ON clientes (activo);

CREATE TABLE IF NOT EXISTS domicilios (
    id                   BIGSERIAL PRIMARY KEY,
    cliente_id           BIGINT       NOT NULL,
    calle                VARCHAR(100) NOT NULL,
    no_exterior          VARCHAR(10)  NOT NULL,
    no_interior          VARCHAR(10),
    colonia_id           BIGINT       NOT NULL,
    municipio_id         BIGINT       NOT NULL,
    estado_id            BIGINT       NOT NULL,
    cp                   VARCHAR(5)   NOT NULL,
    pais_id              BIGINT       NOT NULL,
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT uq_domicilios_cliente UNIQUE (cliente_id),
    CONSTRAINT fk_domicilios_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE CASCADE,
    CONSTRAINT fk_domicilios_colonia FOREIGN KEY (colonia_id) REFERENCES catalogoColonia (id),
    CONSTRAINT fk_domicilios_municipio FOREIGN KEY (municipio_id) REFERENCES catalogoMunicipio (id),
    CONSTRAINT fk_domicilios_estado FOREIGN KEY (estado_id) REFERENCES catalogoEstado (id),
    CONSTRAINT fk_domicilios_pais FOREIGN KEY (pais_id) REFERENCES catalogoPais (id)
);

CREATE TABLE IF NOT EXISTS cuentas (
    id                   BIGSERIAL PRIMARY KEY,
    numero_cuenta        VARCHAR(20)  NOT NULL,
    cliente_id           BIGINT       NOT NULL,
    saldo                NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    estatus              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA',
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT uq_cuentas_numero_cuenta UNIQUE (numero_cuenta),
    CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_cuentas_numero_cuenta ON cuentas (numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_cuentas_cliente_id ON cuentas (cliente_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_estatus ON cuentas (estatus);

CREATE TABLE IF NOT EXISTS usuarios (
    id                   BIGSERIAL PRIMARY KEY,
    cliente_id           BIGINT       NOT NULL,
    correo               VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    rol                  VARCHAR(30)  NOT NULL DEFAULT 'ROLE_CLIENTE',
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT uq_usuarios_cliente UNIQUE (cliente_id),
    CONSTRAINT uq_usuarios_correo UNIQUE (correo),
    CONSTRAINT fk_usuarios_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_usuarios_correo ON usuarios (correo);
CREATE INDEX IF NOT EXISTS idx_usuarios_activo ON usuarios (activo);
