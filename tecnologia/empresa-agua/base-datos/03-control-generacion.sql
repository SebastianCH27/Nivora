USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS estado_simulacion (
    id_estado TINYINT NOT NULL,
    fecha_actual DATE NULL,
    semilla BIGINT NOT NULL DEFAULT 2026,
    actualizado_en TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id_estado),

    CONSTRAINT chk_estado_simulacion_unico
        CHECK (id_estado = 1)
) ENGINE = InnoDB;

-- Repetir este script conserva el avance existente.
INSERT INTO estado_simulacion (id_estado, fecha_actual, semilla)
VALUES (1, NULL, 2026)
ON DUPLICATE KEY UPDATE id_estado = 1;