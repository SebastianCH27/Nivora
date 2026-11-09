-- Ejecutar con una cuenta administradora, despues de 02-suministros.sql.
USE nivora;

-- Coordina las incorporaciones de una misma fuente entre procesos de Nivora.
CREATE TABLE IF NOT EXISTS control_incorporacion_lecturas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (fuente)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS lecturas (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    id_lectura VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    id_medidor VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    fecha_medicion_utc DATETIME(6) NOT NULL,
    valor_litros BIGINT NOT NULL,
    unidad CHAR(1) NOT NULL DEFAULT 'L',
    tipo_medicion VARCHAR(10) NOT NULL DEFAULT 'ACUMULADA',
    recibida_en_utc DATETIME(6) NOT NULL,

    PRIMARY KEY (fuente, id_lectura),
    UNIQUE KEY uq_nivora_medicion (fuente, codigo_suministro, id_medidor, fecha_medicion_utc),
    KEY idx_nivora_lecturas_suministro_fecha (codigo_suministro, fecha_medicion_utc),
    CONSTRAINT fk_nivora_lectura_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_nivora_lectura_valor CHECK (valor_litros >= 0),
    CONSTRAINT chk_nivora_lectura_unidad CHECK (unidad = 'L'),
    CONSTRAINT chk_nivora_lectura_tipo CHECK (tipo_medicion = 'ACUMULADA')
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS incidencias_lecturas (
    id_incidencia BIGINT NOT NULL AUTO_INCREMENT,
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    id_lectura VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    motivo VARCHAR(60) NOT NULL,
    datos_recibidos TEXT NOT NULL,
    huella CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    primera_recepcion_utc DATETIME(6) NOT NULL,
    ultima_recepcion_utc DATETIME(6) NOT NULL,
    repeticiones BIGINT NOT NULL DEFAULT 1,

    PRIMARY KEY (id_incidencia),
    UNIQUE KEY uq_nivora_incidencia_lectura (fuente, id_lectura, huella),
    KEY idx_nivora_incidencias_suministro (codigo_suministro),
    CONSTRAINT fk_nivora_incidencia_suministro FOREIGN KEY (codigo_suministro)
        REFERENCES suministros (codigo),
    CONSTRAINT chk_nivora_incidencia_repeticiones CHECK (repeticiones > 0)
) ENGINE = InnoDB;
