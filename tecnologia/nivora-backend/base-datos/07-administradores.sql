-- Ejecutar como administrador despues de 06-sincronizacion-lecturas.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS administradores (
    usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    hash_clave VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activa BOOLEAN NOT NULL,
    gestionar_administradores BOOLEAN NOT NULL DEFAULT FALSE,
    cambio_clave_requerido BOOLEAN NOT NULL DEFAULT TRUE,
    version_credenciales BIGINT NOT NULL DEFAULT 1,
    creado_en_utc DATETIME(6) NOT NULL,
    actualizado_en_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (usuario),
    CONSTRAINT chk_administrador_version CHECK (version_credenciales > 0)
) ENGINE = InnoDB;

-- Serializa las altas y futuros cambios de cuentas, incluso entre procesos.
CREATE TABLE IF NOT EXISTS control_administradores (
    id_control TINYINT NOT NULL,
    PRIMARY KEY (id_control),
    CONSTRAINT chk_control_administradores CHECK (id_control = 1)
) ENGINE = InnoDB;

INSERT INTO control_administradores (id_control) VALUES (1)
ON DUPLICATE KEY UPDATE id_control = 1;

CREATE TABLE IF NOT EXISTS auditoria_administradores (
    id_evento BIGINT NOT NULL AUTO_INCREMENT,
    actor_usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NULL,
    afectado_usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operacion VARCHAR(40) NOT NULL,
    registrado_en_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (id_evento),
    KEY idx_auditoria_administrador (afectado_usuario, registrado_en_utc),
    CONSTRAINT fk_auditoria_administrador_actor FOREIGN KEY (actor_usuario)
        REFERENCES administradores (usuario),
    CONSTRAINT fk_auditoria_administrador_afectado FOREIGN KEY (afectado_usuario)
        REFERENCES administradores (usuario)
) ENGINE = InnoDB;
