-- Ejecutar despues de 02-suministros.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS tarifas_versiones (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_tarifa VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version_tarifa INT NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    vigente_desde DATE NOT NULL,
    cargo_fijo DECIMAL(12,2) NOT NULL,
    igv_porcentaje DECIMAL(7,4) NULL,
    tramos_json TEXT NOT NULL,
    PRIMARY KEY (fuente, codigo_tarifa, version_tarifa),
    CONSTRAINT chk_tarifa_nivora_version CHECK (version_tarifa >= 1),
    CONSTRAINT chk_tarifa_nivora_cargo CHECK (cargo_fijo >= 0),
    CONSTRAINT chk_tarifa_nivora_igv CHECK (igv_porcentaje BETWEEN 0 AND 100)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS tarifas_historiales (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    revision BIGINT NOT NULL,
    consultado_en_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (fuente, codigo_suministro, revision),
    CONSTRAINT fk_tarifa_historial_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_tarifa_historial_revision CHECK (revision >= 1)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS tarifas_asignaciones (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    revision BIGINT NOT NULL,
    aplicable_desde DATE NOT NULL,
    vigente_hasta DATE NULL,
    asignada_desde DATE NOT NULL,
    asignada_hasta DATE NULL,
    codigo_tarifa VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version_tarifa INT NOT NULL,
    PRIMARY KEY (fuente, codigo_suministro, revision, aplicable_desde),
    CONSTRAINT fk_tarifa_asignacion_historial FOREIGN KEY (fuente, codigo_suministro, revision)
        REFERENCES tarifas_historiales (fuente, codigo_suministro, revision),
    CONSTRAINT fk_tarifa_asignacion_version FOREIGN KEY (fuente, codigo_tarifa, version_tarifa)
        REFERENCES tarifas_versiones (fuente, codigo_tarifa, version_tarifa),
    CONSTRAINT chk_tarifa_asignacion_fechas CHECK (
        asignada_desde <= aplicable_desde
        AND (asignada_hasta IS NULL OR aplicable_desde < asignada_hasta)
        AND (vigente_hasta IS NULL OR aplicable_desde < vigente_hasta)
    )
) ENGINE = InnoDB;
