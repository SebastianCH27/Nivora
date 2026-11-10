-- Ejecutar como administrador despues de 03-lecturas.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS progreso_recuperacion_lecturas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    cursor_confirmado VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    revision BIGINT NOT NULL,
    estado VARCHAR(40) NOT NULL,
    ultima_confirmacion_utc DATETIME(6) NULL,
    actualizado_en_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (fuente, codigo_suministro),
    CONSTRAINT fk_progreso_recuperacion_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_progreso_recuperacion_revision CHECK (revision > 0),
    CONSTRAINT chk_progreso_recuperacion_estado CHECK (estado IN (
        'PENDIENTE', 'EN_CURSO', 'RECORRIDO_COMPLETO', 'ERROR_FUENTE',
        'SUMINISTRO_NO_ENCONTRADO', 'BLOQUEADO'))
) ENGINE = InnoDB;

-- Resume las incidencias de cada tipo y conserva su historial agregado.
CREATE TABLE IF NOT EXISTS incidencias_recuperacion_lecturas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    primera_recepcion_utc DATETIME(6) NOT NULL,
    ultima_recepcion_utc DATETIME(6) NOT NULL,
    repeticiones BIGINT NOT NULL DEFAULT 1,
    abierta BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_resolucion_utc DATETIME(6) NULL,
    PRIMARY KEY (fuente, codigo_suministro, tipo),
    CONSTRAINT fk_incidencia_recuperacion_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_incidencia_recuperacion_tipo CHECK (tipo IN (
        'ERROR_FUENTE', 'SUMINISTRO_NO_ENCONTRADO')),
    CONSTRAINT chk_incidencia_recuperacion_repeticiones CHECK (repeticiones > 0)
) ENGINE = InnoDB;
