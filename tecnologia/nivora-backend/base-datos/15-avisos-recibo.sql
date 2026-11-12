-- Ejecutar despues de 12-recibos.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS avisos_recibo (
    codigo_suministro VARCHAR(20) NOT NULL,
    periodo DATE NOT NULL,
    registrado_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo_suministro, periodo),
    CONSTRAINT fk_aviso_recibo FOREIGN KEY (codigo_suministro, periodo)
        REFERENCES recibos (codigo_suministro, periodo)
) ENGINE = InnoDB;
