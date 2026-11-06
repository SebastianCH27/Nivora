USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Un registro independiente por suministro y mes.
-- periodo corresponde al primer dia del mes en Lima.
-- El consumo se expresa en litros y las horas en UTC.

CREATE TABLE IF NOT EXISTS consumos_mensuales (
    id_suministro BIGINT NOT NULL,
    periodo DATE NOT NULL,
    consumo_litros DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    fecha_actualizacion DATETIME(6) NOT NULL,
    cerrado_en DATETIME(6) NULL,

    PRIMARY KEY (id_suministro, periodo),

    KEY idx_consumos_mensuales_periodo (periodo),

    CONSTRAINT fk_consumo_mensual_suministro
        FOREIGN KEY (id_suministro)
        REFERENCES suministros (id_suministro),

    CONSTRAINT chk_consumo_mensual_periodo
        CHECK (DAYOFMONTH(periodo) = 1),

    CONSTRAINT chk_consumo_mensual_litros
        CHECK (consumo_litros >= 0),

    CONSTRAINT chk_consumo_mensual_cierre
        CHECK (
            cerrado_en IS NULL
            OR cerrado_en >= fecha_actualizacion
        )
) ENGINE = InnoDB;