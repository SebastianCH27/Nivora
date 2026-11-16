-- Ejecutar despues de 13-sincronizacion-recibos.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS incidencias_sincronizacion_recibos (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    motivo VARCHAR(40) NOT NULL,
    primera_recepcion_utc DATETIME(6) NOT NULL,
    ultima_recepcion_utc DATETIME(6) NOT NULL,
    repeticiones BIGINT NOT NULL DEFAULT 1,
    abierta BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_resolucion_utc DATETIME(6) NULL,
    PRIMARY KEY (fuente, codigo_suministro, motivo),
    KEY idx_incidencias_recibos_suministro (codigo_suministro),
    CONSTRAINT fk_incidencia_recibos_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_incidencia_recibos_motivo CHECK (motivo IN (
        'ERROR_FUENTE', 'SUMINISTRO_NO_ENCONTRADO', 'CONFLICTO', 'ERROR_ALMACENAMIENTO', 'ERROR_INTERNO')),
    CONSTRAINT chk_incidencia_recibos_repeticiones CHECK (repeticiones > 0)
) ENGINE = InnoDB;
