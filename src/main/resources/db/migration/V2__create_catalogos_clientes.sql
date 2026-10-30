CREATE TABLE IF NOT EXISTS catalogoGenero (
    id   SERIAL PRIMARY KEY,
    tipo TEXT NOT NULL,
    CONSTRAINT uq_catalogoGenero UNIQUE (tipo)
);

CREATE TABLE IF NOT EXISTS catalogoNacionalidad (
    id     SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL,
    CONSTRAINT uq_catalogoNacionalidad UNIQUE (nombre)
);

CREATE TABLE IF NOT EXISTS catalogoEstadoCivil (
    id     SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL,
    CONSTRAINT uq_catalogoEstadoCivil UNIQUE (nombre)
);

CREATE TABLE IF NOT EXISTS catalogoPais (
    id     SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalogoEstado (
    id     SERIAL PRIMARY KEY,
    estado TEXT NOT NULL,
    pais   BIGINT NOT NULL,
    CONSTRAINT fk_catalogoEstado_pais FOREIGN KEY (pais) REFERENCES catalogoPais (id)
);

CREATE TABLE IF NOT EXISTS catalogoMunicipio (
    id       SERIAL PRIMARY KEY,
    municipio TEXT NOT NULL,
    estado   BIGINT NOT NULL,
    CONSTRAINT fk_catalogoMunicipio_estado FOREIGN KEY (estado) REFERENCES catalogoEstado (id)
);

CREATE TABLE IF NOT EXISTS catalogoColonia (
    id       SERIAL PRIMARY KEY,
    colonia  TEXT NOT NULL,
    municipio BIGINT NOT NULL,
    CONSTRAINT fk_catalogoColonia_municipio FOREIGN KEY (municipio) REFERENCES catalogoMunicipio (id)
);

CREATE TABLE IF NOT EXISTS PersonasFisicas (
    id                SERIAL PRIMARY KEY,
    nombre            TEXT       NOT NULL,
    segundoNombre     TEXT,
    apellidoPaterno   TEXT       NOT NULL,
    apellidoMaterno   TEXT       NOT NULL,
    fechaNacimiento   TIMESTAMP  NOT NULL,
    curp              TEXT       NOT NULL,
    rfc               TEXT       NOT NULL,
    genero            BIGINT     NOT NULL,
    nacionalidad      BIGINT     NOT NULL,
    estadoCivil       BIGINT     NOT NULL,
    correo            TEXT       NOT NULL,
    lada              SMALLINT   NOT NULL,
    numeroTelefono    INTEGER    NOT NULL,
    numeroTelefono2   INTEGER,
    calle             TEXT       NOT NULL,
    noExterior        SMALLINT   NOT NULL,
    noInterior        SMALLINT,
    colonia           BIGINT     NOT NULL,
    municipio         BIGINT     NOT NULL,
    estado            BIGINT     NOT NULL,
    cp                INTEGER,
    pais              BIGINT     NOT NULL,
    CONSTRAINT fk_PersonasFisicas_genero FOREIGN KEY (genero) REFERENCES catalogoGenero (id),
    CONSTRAINT fk_PersonasFisicas_nacionalidad FOREIGN KEY (nacionalidad) REFERENCES catalogoNacionalidad (id),
    CONSTRAINT fk_PersonasFisicas_estadoCivil FOREIGN KEY (estadoCivil) REFERENCES catalogoEstadoCivil (id),
    CONSTRAINT fk_PersonasFisicas_colonia FOREIGN KEY (colonia) REFERENCES catalogoColonia (id),
    CONSTRAINT fk_PersonasFisicas_municipio FOREIGN KEY (municipio) REFERENCES catalogoMunicipio (id),
    CONSTRAINT fk_PersonasFisicas_estado FOREIGN KEY (estado) REFERENCES catalogoEstado (id),
    CONSTRAINT fk_PersonasFisicas_pais FOREIGN KEY (pais) REFERENCES catalogoPais (id)
);
