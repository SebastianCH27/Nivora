-- Ejecutar con una cuenta administradora, con Nivora detenido, después del script 20.
-- Las instalaciones nuevas ya incluyen estas columnas y restricciones en el script 03.
USE nivora;

-- Un solo ALTER conserva los registros y cambia la restricción de forma atómica en MySQL 8.
-- La guarda permite repetir este script sobre las dos versiones conocidas del esquema.
SET @nivora_ddl_intervalos = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'lecturas'
            AND column_name = 'inicio_intervalo_utc'),
    'SELECT 1',
    'ALTER TABLE lecturas
        ADD COLUMN inicio_intervalo_utc DATETIME(6) NULL,
        DROP CONSTRAINT chk_nivora_lectura_tipo,
        ADD CONSTRAINT chk_nivora_lectura_intervalo CHECK (
            (tipo_medicion = ''ACUMULADA'' AND inicio_intervalo_utc IS NULL)
            OR (tipo_medicion = ''INTERVALO'' AND inicio_intervalo_utc IS NOT NULL
                AND inicio_intervalo_utc < fecha_medicion_utc))');
PREPARE nivora_migrar_intervalos FROM @nivora_ddl_intervalos;
EXECUTE nivora_migrar_intervalos;
DEALLOCATE PREPARE nivora_migrar_intervalos;
SET @nivora_ddl_intervalos = NULL;
