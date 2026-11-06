USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS control_consumo (
    id_control TINYINT NOT NULL,
    actualizado_hasta DATETIME(6) NULL,
    semilla BIGINT NOT NULL DEFAULT 2026,

    PRIMARY KEY (id_control),

    CONSTRAINT chk_control_consumo_unico
        CHECK (id_control = 1)
) ENGINE = InnoDB;

INSERT INTO control_consumo (
    id_control, actualizado_hasta, semilla
)
VALUES (1, NULL, 2026)
ON DUPLICATE KEY UPDATE id_control = 1;