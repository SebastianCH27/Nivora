-- Ejecutar como administrador despues de 08-sesiones-administrativas.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS sesiones_consulta (
    id_sesion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hash_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    dni_utilizado CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nombre_consultado TEXT NOT NULL,
    creada_en_utc DATETIME(6) NOT NULL,
    expira_en_utc DATETIME(6) NOT NULL,
    revocada_en_utc DATETIME(6) NULL,
    PRIMARY KEY (id_sesion),
    UNIQUE KEY uq_sesion_consulta_token (hash_token),
    KEY idx_sesion_consulta_suministro_fecha (codigo_suministro, creada_en_utc),
    CONSTRAINT fk_sesion_consulta_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_sesion_consulta_dni CHECK (
        CHAR_LENGTH(dni_utilizado) = 8 AND NOT REGEXP_LIKE(dni_utilizado, '[^0-9]')),
    CONSTRAINT chk_sesion_consulta_nombre CHECK (CHAR_LENGTH(TRIM(nombre_consultado)) > 0),
    CONSTRAINT chk_sesion_consulta_fechas CHECK (
        creada_en_utc < expira_en_utc AND
        (revocada_en_utc IS NULL OR revocada_en_utc >= creada_en_utc))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS limites_acceso_consulta (
    clave CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inicio_ventana_utc DATETIME(6) NOT NULL,
    cantidad INT NOT NULL,
    PRIMARY KEY (clave),
    CONSTRAINT chk_limite_consulta_cantidad CHECK (cantidad > 0)
) ENGINE = InnoDB;
