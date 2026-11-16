-- Ejecutar despues de 07-administradores.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS control_periodos_mensuales (
    id_control TINYINT NOT NULL,
    PRIMARY KEY (id_control),
    CONSTRAINT chk_control_periodos_mensuales CHECK (id_control = 1)
) ENGINE = InnoDB;

INSERT INTO control_periodos_mensuales (id_control) VALUES (1)
ON DUPLICATE KEY UPDATE id_control = 1;

CREATE TABLE IF NOT EXISTS publicaciones_periodos_mensuales (
    revision BIGINT NOT NULL,
    desde_mes DATE NOT NULL,
    dia_inicio TINYINT NOT NULL,
    publicada_en_utc DATETIME(6) NOT NULL,
    actor_usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (revision),
    KEY idx_periodo_publicado (desde_mes, revision),
    CONSTRAINT fk_periodo_mensual_actor FOREIGN KEY (actor_usuario)
        REFERENCES administradores (usuario),
    CONSTRAINT chk_periodo_mensual_revision CHECK (revision > 0),
    CONSTRAINT chk_periodo_mensual_desde CHECK (EXTRACT(DAY FROM desde_mes) = 1),
    CONSTRAINT chk_periodo_mensual_dia CHECK (dia_inicio BETWEEN 1 AND 31)
) ENGINE = InnoDB;
