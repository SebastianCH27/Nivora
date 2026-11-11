-- Ejecutar como administrador despues de 07-administradores.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS sesiones_administrativas (
    id_sesion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hash_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_credenciales BIGINT NOT NULL,
    creada_en_utc DATETIME(6) NOT NULL,
    ultima_actividad_utc DATETIME(6) NOT NULL,
    expira_en_utc DATETIME(6) NOT NULL,
    inactiva_hasta_utc DATETIME(6) NOT NULL,
    revocada_en_utc DATETIME(6) NULL,
    PRIMARY KEY (id_sesion),
    UNIQUE KEY uq_sesion_administrativa_token (hash_token),
    KEY idx_sesion_administrativa_usuario (usuario),
    KEY idx_sesion_administrativa_expiracion (expira_en_utc),
    CONSTRAINT fk_sesion_administrativa_usuario FOREIGN KEY (usuario)
        REFERENCES administradores (usuario),
    CONSTRAINT chk_sesion_administrativa_version CHECK (version_credenciales > 0),
    CONSTRAINT chk_sesion_administrativa_fechas CHECK (
        creada_en_utc <= ultima_actividad_utc AND ultima_actividad_utc < expira_en_utc
        AND inactiva_hasta_utc <= expira_en_utc)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS limites_acceso_administrativo (
    clave CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inicio_ventana_utc DATETIME(6) NOT NULL,
    cantidad INT NOT NULL,
    PRIMARY KEY (clave),
    KEY idx_limite_administrativo_ventana (inicio_ventana_utc),
    CONSTRAINT chk_limite_administrativo_cantidad CHECK (cantidad > 0)
) ENGINE = InnoDB;
