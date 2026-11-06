-- Empresa de Agua: tarifas y recibos.
-- Importes expresados en soles y consumos en metros cubicos.
-- Las fechas "hasta" de tarifas y asignaciones son exclusivas.

USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Cada cambio tarifario se registra como una nueva version.
CREATE TABLE IF NOT EXISTS tarifas (
    id_tarifa BIGINT NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    version INT NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    cargo_fijo DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    vigente_desde DATE NOT NULL,
    vigente_hasta DATE NULL,

    PRIMARY KEY (id_tarifa),

    UNIQUE KEY uq_tarifa_codigo_version (
        codigo,
        version
    ),

    CONSTRAINT chk_tarifa_version
        CHECK (version >= 1),

    CONSTRAINT chk_tarifa_cargo_fijo
        CHECK (cargo_fijo >= 0),

    CONSTRAINT chk_tarifa_vigencia
        CHECK (
            vigente_hasta IS NULL
            OR vigente_hasta > vigente_desde
        )
) ENGINE = InnoDB;

-- Cada tramo establece el precio de una parte del consumo.
-- Un limite superior NULL representa el ultimo tramo sin tope.
CREATE TABLE IF NOT EXISTS tramos_tarifa (
    id_tramo BIGINT NOT NULL AUTO_INCREMENT,
    id_tarifa BIGINT NOT NULL,
    desde_m3 DECIMAL(15,3) NOT NULL,
    hasta_m3 DECIMAL(15,3) NULL,
    precio_por_m3 DECIMAL(12,4) NOT NULL,

    PRIMARY KEY (id_tramo),

    UNIQUE KEY uq_tramo_tarifa_inicio (
        id_tarifa,
        desde_m3
    ),

    CONSTRAINT fk_tramo_tarifa
        FOREIGN KEY (id_tarifa)
        REFERENCES tarifas (id_tarifa),

    CONSTRAINT chk_tramo_inicio
        CHECK (desde_m3 >= 0),

    CONSTRAINT chk_tramo_limites
        CHECK (
            hasta_m3 IS NULL
            OR hasta_m3 > desde_m3
        ),

    CONSTRAINT chk_tramo_precio
        CHECK (precio_por_m3 >= 0)
) ENGINE = InnoDB;

-- Historial de tarifas asignadas a cada suministro.
CREATE TABLE IF NOT EXISTS suministro_tarifas (
    id_asignacion BIGINT NOT NULL AUTO_INCREMENT,
    id_suministro BIGINT NOT NULL,
    id_tarifa BIGINT NOT NULL,
    fecha_desde DATE NOT NULL,
    fecha_hasta DATE NULL,

    PRIMARY KEY (id_asignacion),

    UNIQUE KEY uq_suministro_tarifa_inicio (
        id_suministro,
        fecha_desde
    ),

    CONSTRAINT fk_asignacion_suministro
        FOREIGN KEY (id_suministro)
        REFERENCES suministros (id_suministro),

    CONSTRAINT fk_asignacion_tarifa
        FOREIGN KEY (id_tarifa)
        REFERENCES tarifas (id_tarifa),

    CONSTRAINT chk_asignacion_fechas
        CHECK (
            fecha_hasta IS NULL
            OR fecha_hasta > fecha_desde
        )
) ENGINE = InnoDB;

-- Recibos mensuales proporcionados por la Empresa.
-- El periodo se identifica con el primer dia del mes.
-- Disponibilidad y vencimiento son fechas del calendario de Lima.
CREATE TABLE IF NOT EXISTS recibos (
    id_recibo BIGINT NOT NULL AUTO_INCREMENT,
    numero_recibo VARCHAR(40) NOT NULL,
    id_suministro BIGINT NOT NULL,
    periodo DATE NOT NULL,
    importe_total DECIMAL(12,2) NOT NULL,
    fecha_disponibilidad DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    fecha_registro TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id_recibo),

    UNIQUE KEY uq_recibo_numero (numero_recibo),

    UNIQUE KEY uq_recibo_suministro_periodo (
        id_suministro,
        periodo
    ),

    CONSTRAINT fk_recibo_suministro
        FOREIGN KEY (id_suministro)
        REFERENCES suministros (id_suministro),

    CONSTRAINT chk_recibo_periodo
        CHECK (DAYOFMONTH(periodo) = 1),

    CONSTRAINT chk_recibo_importe
        CHECK (importe_total >= 0),

    CONSTRAINT chk_recibo_disponibilidad
        CHECK (fecha_disponibilidad > LAST_DAY(periodo)),

    CONSTRAINT chk_recibo_vencimiento
        CHECK (fecha_vencimiento >= fecha_disponibilidad)
) ENGINE = InnoDB;