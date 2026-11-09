-- Ejecutar con una cuenta administradora y el servicio Empresa de Agua detenido.
-- Apoya la consulta paginada por suministro e identificador de lectura.
USE empresa_agua;

SET @empresa_indice_lecturas_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
            AND table_name = 'lecturas'
            AND index_name = 'idx_lecturas_suministro_id'
    ),
    'SELECT ''El indice de lecturas ya existe.'' AS resultado',
    'CREATE INDEX idx_lecturas_suministro_id ON lecturas (id_suministro, id_lectura)'
);

PREPARE empresa_crear_indice_lecturas FROM @empresa_indice_lecturas_sql;
EXECUTE empresa_crear_indice_lecturas;
DEALLOCATE PREPARE empresa_crear_indice_lecturas;
