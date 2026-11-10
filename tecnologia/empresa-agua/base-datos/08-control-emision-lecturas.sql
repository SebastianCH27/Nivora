-- Ejecutar con una cuenta administradora y la Empresa de Agua detenida.
-- Controla la ultima emision continua de lecturas confirmada, en UTC.
USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS control_emision_lecturas (
    id_control TINYINT NOT NULL,
    ultima_emision_utc DATETIME(6) NULL,

    PRIMARY KEY (id_control),

    CONSTRAINT chk_control_emision_lecturas_unico
        CHECK (id_control = 1)
) ENGINE = InnoDB;

INSERT INTO control_emision_lecturas (
    id_control, ultima_emision_utc
)
VALUES (1, NULL)
ON DUPLICATE KEY UPDATE id_control = 1;
