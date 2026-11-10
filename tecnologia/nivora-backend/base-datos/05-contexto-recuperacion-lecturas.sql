-- Ejecutar como administrador despues de 04-recuperacion-lecturas.sql.
-- Conserva las lecturas, incidencias y progresos existentes.
USE nivora;

CREATE TABLE IF NOT EXISTS contexto_recuperacion_lecturas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    hasta_id_lectura VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmado_hasta_utc DATETIME(6) NOT NULL,
    inventario_json MEDIUMTEXT NOT NULL,
    capturado_en_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (fuente, codigo_suministro),
    CONSTRAINT fk_contexto_recuperacion_progreso
        FOREIGN KEY (fuente, codigo_suministro)
        REFERENCES progreso_recuperacion_lecturas (fuente, codigo_suministro)
) ENGINE = InnoDB;
