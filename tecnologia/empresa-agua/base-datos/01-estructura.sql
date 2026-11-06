-- Empresa de Agua: estructura inicial.
-- Los registros utilizados en el proyecto seran ficticios.

CREATE DATABASE IF NOT EXISTS empresa_agua
CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;

USE empresa_agua;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Personas asociadas a los suministros.
CREATE TABLE IF NOT EXISTS titulares (
    id_titular BIGINT NOT NULL AUTO_INCREMENT,
    nombre_completo VARCHAR(160) NOT NULL,

    PRIMARY KEY (id_titular),

    CONSTRAINT chk_titular_nombre
        CHECK (CHAR_LENGTH(TRIM(nombre_completo)) > 0)
) ENGINE = InnoDB;

-- Cada codigo identifica un suministro.
CREATE TABLE IF NOT EXISTS suministros (
    id_suministro BIGINT NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(20) NOT NULL,
    id_titular BIGINT NOT NULL,
    direccion VARCHAR(200) NOT NULL,

    PRIMARY KEY (id_suministro),
    UNIQUE KEY uq_suministro_codigo (codigo),

    CONSTRAINT fk_suministro_titular
        FOREIGN KEY (id_titular)
        REFERENCES titulares (id_titular),

    CONSTRAINT chk_suministro_codigo
        CHECK (CHAR_LENGTH(TRIM(codigo)) > 0)
) ENGINE = InnoDB;

-- Permite conservar los medidores anteriores de un suministro.
CREATE TABLE IF NOT EXISTS medidores (
    id_medidor BIGINT NOT NULL AUTO_INCREMENT,
    numero_serie VARCHAR(30) NOT NULL,
    id_suministro BIGINT NOT NULL,
    fecha_instalacion DATE NOT NULL,
    fecha_retiro DATE NULL,

    PRIMARY KEY (id_medidor),
    UNIQUE KEY uq_medidor_serie (numero_serie),
    UNIQUE KEY uq_medidor_suministro (
        id_medidor,
        id_suministro
    ),

    CONSTRAINT fk_medidor_suministro
        FOREIGN KEY (id_suministro)
        REFERENCES suministros (id_suministro),

    CONSTRAINT chk_medidor_fechas
        CHECK (
            fecha_retiro IS NULL
            OR fecha_retiro >= fecha_instalacion
        )
) ENGINE = InnoDB;

-- Cada fila representa una medicion acumulada en metros cubicos.
-- fecha_medicion se expresara en UTC.
CREATE TABLE IF NOT EXISTS lecturas (
    id_lectura BIGINT NOT NULL AUTO_INCREMENT,
    id_suministro BIGINT NOT NULL,
    id_medidor BIGINT NOT NULL,
    fecha_medicion DATETIME(6) NOT NULL,
    lectura_acumulada_m3 DECIMAL(15,3) NOT NULL,
    fecha_registro TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id_lectura),

    UNIQUE KEY uq_lectura_medidor_fecha (
        id_medidor,
        id_suministro,
        fecha_medicion
    ),

    KEY idx_lecturas_suministro_fecha (
        id_suministro,
        fecha_medicion
    ),

    CONSTRAINT fk_lectura_medidor_suministro
        FOREIGN KEY (id_medidor, id_suministro)
        REFERENCES medidores (id_medidor, id_suministro),

    CONSTRAINT chk_lectura_no_negativa
        CHECK (lectura_acumulada_m3 >= 0)
) ENGINE = InnoDB;