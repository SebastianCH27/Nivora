-- Ejecutar despues de 17-configuracion-notificaciones.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS suscripciones_web_push (
    id_suscripcion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    id_dispositivo CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    huella_endpoint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endpoint VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    p256dh CHAR(87) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth CHAR(22) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registrada_en_utc DATETIME(6) NOT NULL,
    expirada_en_utc DATETIME(6) NULL,

    PRIMARY KEY (id_suscripcion),
    UNIQUE KEY uq_web_push_dispositivo (codigo_suministro, id_dispositivo),
    UNIQUE KEY uq_web_push_endpoint (huella_endpoint),
    CONSTRAINT fk_web_push_preferencia FOREIGN KEY (codigo_suministro, id_dispositivo)
        REFERENCES preferencias_notificaciones_dispositivo (codigo_suministro, id_dispositivo)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS entregas_web_push (
    id_suscripcion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    periodo DATE NOT NULL,
    aviso_registrado_en_utc DATETIME(6) NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    intentos INT NOT NULL DEFAULT 0,
    proximo_intento_utc DATETIME(6) NOT NULL,
    reserva_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reserva_hasta_utc DATETIME(6) NULL,

    PRIMARY KEY (id_suscripcion, tipo, periodo),
    KEY idx_web_push_pendientes (estado, proximo_intento_utc),
    CONSTRAINT fk_entrega_web_push_suscripcion FOREIGN KEY (id_suscripcion)
        REFERENCES suscripciones_web_push (id_suscripcion) ON DELETE CASCADE,
    CONSTRAINT chk_entrega_web_push_estado CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'FALLIDA', 'EXPIRADA')),
    CONSTRAINT chk_entrega_web_push_tipo CHECK (tipo IN ('ALERTA_CONSUMO', 'RECIBO_DISPONIBLE')),
    CONSTRAINT chk_entrega_web_push_reserva CHECK ((reserva_token IS NULL) = (reserva_hasta_utc IS NULL)),
    CONSTRAINT chk_entrega_web_push_intentos CHECK (intentos >= 0)
) ENGINE = InnoDB;
