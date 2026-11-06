USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS detalle_recibos (
    id_recibo BIGINT NOT NULL,
    id_tarifa BIGINT NOT NULL,
    consumo_litros DECIMAL(15,3) NOT NULL,
    cargo_fijo DECIMAL(12,2) NOT NULL,
    importe_consumo DECIMAL(12,2) NOT NULL,

    PRIMARY KEY (id_recibo),

    CONSTRAINT fk_detalle_recibo
        FOREIGN KEY (id_recibo)
        REFERENCES recibos (id_recibo),

    CONSTRAINT fk_detalle_tarifa
        FOREIGN KEY (id_tarifa)
        REFERENCES tarifas (id_tarifa),

    CONSTRAINT chk_detalle_consumo
        CHECK (consumo_litros >= 0),

    CONSTRAINT chk_detalle_cargo
        CHECK (cargo_fijo >= 0),

    CONSTRAINT chk_detalle_importe
        CHECK (importe_consumo >= 0)
) ENGINE = InnoDB;