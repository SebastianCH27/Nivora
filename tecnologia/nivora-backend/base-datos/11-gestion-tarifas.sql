-- Ejecutar despues de 07-administradores.sql y 10-tarifas.sql.
USE nivora;

CREATE TABLE IF NOT EXISTS tarifas_publicaciones (
    fuente VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    codigo_suministro VARCHAR(20) NOT NULL,
    revision BIGINT NOT NULL,
    actor_usuario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (fuente, codigo_suministro, revision),
    CONSTRAINT fk_tarifa_publicacion_revision FOREIGN KEY (fuente, codigo_suministro, revision)
        REFERENCES tarifas_historiales (fuente, codigo_suministro, revision),
    CONSTRAINT fk_tarifa_publicacion_actor FOREIGN KEY (actor_usuario)
        REFERENCES administradores (usuario),
    CONSTRAINT chk_tarifa_publicacion_fuente CHECK (fuente = 'nivora')
) ENGINE = InnoDB;
