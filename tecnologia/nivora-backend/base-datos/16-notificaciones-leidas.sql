-- Ejecutar despues de 14-alertas-consumo.sql y 15-avisos-recibo.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS notificaciones_leidas (
    codigo_suministro VARCHAR(20) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    periodo DATE NOT NULL,
    leida_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo_suministro, tipo, periodo),
    CONSTRAINT fk_notificacion_leida_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_notificacion_leida_tipo CHECK (tipo IN ('ALERTA_CONSUMO', 'RECIBO_DISPONIBLE')),
    CONSTRAINT chk_notificacion_leida_periodo CHECK (EXTRACT(DAY FROM periodo) = 1)
) ENGINE = InnoDB;
