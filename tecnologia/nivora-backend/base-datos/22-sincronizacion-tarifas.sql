-- Ejecutar como administrador con Nivora detenido, despues del script 21.
-- Repetir el script conserva historiales, programaciones y reservas existentes.
USE nivora;

CREATE TABLE IF NOT EXISTS programacion_sincronizacion_tarifas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    proximo_intento_utc DATETIME(6) NOT NULL,
    fallos_consecutivos INT NOT NULL DEFAULT 0,
    reserva_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reserva_hasta_utc DATETIME(6) NULL,
    ultimo_intento_utc DATETIME(6) NULL,
    ultimo_exito_utc DATETIME(6) NULL,
    ultimo_resultado VARCHAR(40) NULL,

    PRIMARY KEY (fuente, codigo_suministro),
    KEY idx_sinc_tarifas_pendiente (fuente, proximo_intento_utc, codigo_suministro),
    CONSTRAINT fk_sinc_tarifas_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_sinc_tarifas_fallos CHECK (fallos_consecutivos >= 0),
    CONSTRAINT chk_sinc_tarifas_reserva CHECK (
        (reserva_token IS NULL AND reserva_hasta_utc IS NULL)
        OR (reserva_token IS NOT NULL AND reserva_hasta_utc IS NOT NULL)
    )
) ENGINE = InnoDB;
