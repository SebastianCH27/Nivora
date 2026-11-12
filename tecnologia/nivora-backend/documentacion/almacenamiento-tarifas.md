# Incorporación de tarifas en Nivora

La parte 2 del bloque 5 consulta el historial de un suministro registrado
en Nivora y conserva en MySQL las versiones devueltas por Empresa de Agua.
El importador valida el suministro, PEN, el calendario de Lima, los
intervalos y la continuidad de los tramos. La selección local requiere una
asignación que cubra el intervalo completo de fechas `[desde, hasta)`.

El precio `precioPorM3` de la API representa agua. El modelo también admite
`precioAlcantarilladoPorM3` en cada tramo e `igvPorcentaje` en la versión.
Esos campos son `null` cuando la fuente los omite. Un cero informado se
conserva como cero. La tarifa `DOMESTICA` de la fuente actual contiene
los importes de la simulación.

| Tabla | Contenido |
|---|---|
| `tarifas_versiones` | Condiciones económicas por fuente, código y número de versión. Conserva el cargo fijo, la tasa de IGV y los tramos con precios separados de agua y alcantarillado. |
| `tarifas_historiales` | Revisiones consultadas para cada suministro, con su marca UTC. |
| `tarifas_asignaciones` | Fechas de vigencia y asignación de cada versión en la revisión. |

Un cambio de asignaciones o vigencias genera una revisión y conserva las
anteriores. Repetir los mismos datos actualiza la fecha de consulta de la
misma revisión. Una respuesta anterior a la última consulta registrada
conserva la revisión más reciente. Un fallo HTTP, una ausencia confirmada
del suministro o un cambio de las condiciones económicas dentro de la
misma versión conserva los datos ya guardados. Una lista vacía confirmada
para un suministro existente se registra como una revisión sin asignaciones.

## Preparar MySQL

En el cliente MySQL, con la cuenta administradora, aplica el script después
de haber creado la tabla `suministros`.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/10-tarifas.sql;
SHOW TABLES FROM nivora LIKE 'tarifas_%';
```

Deben aparecer `tarifas_asignaciones`, `tarifas_historiales` y
`tarifas_versiones`.

## Incorporar un suministro de prueba

Con Empresa de Agua funcionando en 8081, ejecuta desde el CMD del módulo
`nivora-backend` el JAR compilado. Usa un suministro previamente registrado
en Nivora. El comando predeterminado usa `13100390`.

```bat
powershell -NoProfile -Command "& { $clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' --spring.main.web-application-type=none --nivora.sincronizacion.habilitada=false --nivora.tarifas.comprobar-al-iniciar=true }"
```

El proceso termina después de imprimir el suministro, la revisión y el
número de asignaciones. La primera importación de la tarifa de demostración
muestra revisión 1 con una asignación. Al repetir el comando con la misma
respuesta, la revisión permanece en 1.

Para verificar los valores guardados, ejecuta en el cliente MySQL.

```sql
SELECT a.revision, a.aplicable_desde, a.codigo_tarifa, a.version_tarifa, v.cargo_fijo, v.igv_porcentaje, v.tramos_json FROM nivora.tarifas_asignaciones a JOIN nivora.tarifas_versiones v ON v.fuente = a.fuente AND v.codigo_tarifa = a.codigo_tarifa AND v.version_tarifa = a.version_tarifa WHERE a.codigo_suministro = '13100390' ORDER BY a.revision, a.aplicable_desde;
```
