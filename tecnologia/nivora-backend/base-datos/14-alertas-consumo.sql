-- Ejecutar despues de 02-suministros.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS alertas_consumo (
    codigo_suministro VARCHAR(20) NOT NULL,
    periodo DATE NOT NULL,
    consumo_actual_litros BIGINT NOT NULL,
    consumo_anterior_litros BIGINT NOT NULL,
    medido_hasta_utc DATETIME(6) NOT NULL,
    detectada_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo_suministro, periodo),
    CONSTRAINT fk_alerta_consumo_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_alerta_consumo_periodo CHECK (EXTRACT(DAY FROM periodo) = 1),
    CONSTRAINT chk_alerta_consumo_superacion CHECK (
        consumo_anterior_litros >= 0 AND consumo_actual_litros > consumo_anterior_litros
    ),
    CONSTRAINT chk_alerta_consumo_fechas CHECK (medido_hasta_utc <= detectada_en_utc)
) ENGINE = InnoDB;
