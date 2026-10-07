-- Migración V5: Carga inicial (seed) de datos en los catálogos del sistema bancario.
-- Los insertos son idempotentes para poder re-ejecutarse sin duplicar información.

-- Catálogo de género / sexo
INSERT INTO catalogoGenero (tipo) VALUES
    ('Masculino'),
    ('Femenino'),
    ('Otro')
ON CONFLICT (tipo) DO NOTHING;

-- Catálogo de nacionalidad
INSERT INTO catalogoNacionalidad (nombre) VALUES
    ('Mexicana'),
    ('Estadounidense'),
    ('Canadiense'),
    ('Espanola'),
    ('Argentina'),
    ('Colombiana'),
    ('Otra')
ON CONFLICT (nombre) DO NOTHING;

-- Catálogo de estado civil
INSERT INTO catalogoEstadoCivil (nombre) VALUES
    ('Soltero'),
    ('Casado'),
    ('Divorciado'),
    ('Viudo'),
    ('Union libre')
ON CONFLICT (nombre) DO NOTHING;

-- Catálogo de países
INSERT INTO catalogoPais (nombre)
SELECT v.nombre
FROM (VALUES
    ('Mexico'),
    ('Estados Unidos'),
    ('Canada'),
    ('Espana'),
    ('Argentina'),
    ('Colombia')
) AS v(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM catalogoPais p WHERE p.nombre = v.nombre
);

-- Catálogo de estados (México)
INSERT INTO catalogoEstado (estado, pais)
SELECT v.estado, p.id
FROM (VALUES
    ('Ciudad de Mexico'),
    ('Jalisco'),
    ('Nuevo Leon'),
    ('Estado de Mexico'),
    ('Puebla'),
    ('Yucatan')
) AS v(estado)
JOIN catalogoPais p ON p.nombre = 'Mexico'
WHERE NOT EXISTS (
    SELECT 1 FROM catalogoEstado e WHERE e.estado = v.estado AND e.pais = p.id
);

-- Catálogo de municipios
INSERT INTO catalogoMunicipio (municipio, estado)
SELECT v.municipio, e.id
FROM (VALUES
    ('Cuauhtemoc', 'Ciudad de Mexico'),
    ('Benito Juarez', 'Ciudad de Mexico'),
    ('Miguel Hidalgo', 'Ciudad de Mexico'),
    ('Guadalajara', 'Jalisco'),
    ('Zapopan', 'Jalisco'),
    ('Tlaquepaque', 'Jalisco'),
    ('Monterrey', 'Nuevo Leon'),
    ('San Pedro Garza Garcia', 'Nuevo Leon'),
    ('Guadalupe', 'Nuevo Leon'),
    ('Puebla', 'Puebla'),
    ('Merida', 'Yucatan')
) AS v(municipio, estadoNombre)
JOIN catalogoEstado e ON e.estado = v.estadoNombre
JOIN catalogoPais p ON p.id = e.pais AND p.nombre = 'Mexico'
WHERE NOT EXISTS (
    SELECT 1 FROM catalogoMunicipio m WHERE m.municipio = v.municipio AND m.estado = e.id
);

-- Catálogo de colonias
INSERT INTO catalogoColonia (colonia, municipio)
SELECT v.colonia, m.id
FROM (VALUES
    ('Centro',              'Cuauhtemoc',          'Ciudad de Mexico'),
    ('Roma Norte',          'Cuauhtemoc',          'Ciudad de Mexico'),
    ('Condesa',             'Cuauhtemoc',          'Ciudad de Mexico'),
    ('Del Valle',           'Benito Juarez',       'Ciudad de Mexico'),
    ('Polanco',             'Miguel Hidalgo',      'Ciudad de Mexico'),
    ('Centro',              'Guadalajara',         'Jalisco'),
    ('Chapalita',           'Guadalajara',         'Jalisco'),
    ('Providencia',         'Guadalajara',         'Jalisco'),
    ('Ciudad Granja',       'Zapopan',             'Jalisco'),
    ('Centro',              'Monterrey',           'Nuevo Leon'),
    ('San Pedro',           'San Pedro Garza Garcia', 'Nuevo Leon'),
    ('Centro',              'Merida',              'Yucatan')
) AS v(colonia, municipioNombre, estadoNombre)
JOIN catalogoMunicipio m ON m.municipio = v.municipioNombre
JOIN catalogoEstado e ON e.id = m.estado AND e.estado = v.estadoNombre
WHERE NOT EXISTS (
    SELECT 1 FROM catalogoColonia c WHERE c.colonia = v.colonia AND c.municipio = m.id
);
