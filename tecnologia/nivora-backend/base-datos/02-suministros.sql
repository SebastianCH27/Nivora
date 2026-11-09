-- Ejecutar con una cuenta administradora de MySQL, despues de 01-base-datos.sql.
-- Conserva solo suministros verificados; no copia el padron completo de la empresa.
USE nivora;

CREATE TABLE IF NOT EXISTS suministros (
    codigo VARCHAR(20) NOT NULL,
    titular TEXT NOT NULL,
    verificado_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (codigo),

    CONSTRAINT chk_nivora_suministro_codigo
        CHECK (
            CHAR_LENGTH(codigo) BETWEEN 1 AND 20
            AND NOT REGEXP_LIKE(codigo, '[^0-9]')
        ),

    CONSTRAINT chk_nivora_suministro_titular
        CHECK (CHAR_LENGTH(TRIM(titular)) > 0)
) ENGINE = InnoDB;
