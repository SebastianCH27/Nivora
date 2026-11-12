-- Ejecutar despues de 02-suministros.sql y 07-administradores.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS preferencias_notificaciones_dispositivo (
    codigo_suministro VARCHAR(20) NOT NULL,
    id_dispositivo CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permiso VARCHAR(12) NOT NULL,
    habilitadas BOOLEAN NOT NULL,
    actualizada_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo_suministro, id_dispositivo),
    CONSTRAINT fk_preferencia_notificaciones_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_preferencia_notificaciones_permiso CHECK (permiso IN ('CONCEDIDO', 'DENEGADO', 'PENDIENTE')),
    CONSTRAINT chk_preferencia_notificaciones_habilitadas CHECK (NOT habilitadas OR permiso = 'CONCEDIDO')
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS configuracion_alertas_consumo (
    id_configuracion TINYINT NOT NULL,
    habilitada BOOLEAN NOT NULL,
    actualizada_por VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actualizada_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (id_configuracion),
    CONSTRAINT chk_configuracion_alertas_consumo_id CHECK (id_configuracion = 1),
    CONSTRAINT fk_configuracion_alertas_administrador FOREIGN KEY (actualizada_por)
        REFERENCES administradores (usuario)
) ENGINE = InnoDB;
