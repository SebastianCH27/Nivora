-- Ejecutar despues de 02-suministros.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS recibos (
    codigo_suministro VARCHAR(20) NOT NULL,
    periodo DATE NOT NULL,
    numero_recibo VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    consumo_litros DECIMAL(15,3) NOT NULL,
    cargo_fijo DECIMAL(12,2) NOT NULL,
    importe_consumo DECIMAL(12,2) NOT NULL,
    importe_total DECIMAL(12,2) NOT NULL,
    fecha_disponibilidad DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    codigo_tarifa VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version_tarifa INT NOT NULL,
    recibido_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo_suministro, periodo),
    CONSTRAINT uq_nivora_recibo_numero UNIQUE (numero_recibo),
    CONSTRAINT fk_nivora_recibo_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_nivora_recibo_periodo CHECK (EXTRACT(DAY FROM periodo) = 1),
    CONSTRAINT chk_nivora_recibo_fechas CHECK (
        fecha_disponibilidad > periodo AND fecha_vencimiento >= fecha_disponibilidad
    ),
    CONSTRAINT chk_nivora_recibo_importes CHECK (
        consumo_litros >= 0 AND cargo_fijo >= 0 AND importe_consumo >= 0
        AND importe_total = cargo_fijo + importe_consumo
    ),
    CONSTRAINT chk_nivora_recibo_version CHECK (version_tarifa >= 1)
) ENGINE = InnoDB;
