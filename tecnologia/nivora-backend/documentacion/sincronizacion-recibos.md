# Sincronización automática de recibos

Nivora consulta Empresa de Agua para cada suministro registrado localmente.
Cada intento consulta hasta 60 recibos disponibles. El importador valida
y guarda los recibos nuevos mediante las reglas de
[almacenamiento de recibos](almacenamiento-recibos.md). Las facturas
aceptadas quedan disponibles en MySQL durante un fallo del proveedor.

## Calendario persistido

La tarea selecciona los próximos intentos vencidos al comenzar cada ciclo.
Cada intento reserva su suministro por un tiempo definido. La reserva
se libera al guardar el resultado y caduca si el proceso se interrumpe.
Otra instancia puede tomar una reserva vencida. La confirmación comprueba
el token y el vencimiento antes de modificar el calendario.

| Resultado | Próximo intento |
|---|---|
| `ACTUALIZADO` | Reinicia el contador de fallos y usa el intervalo completo. Una respuesta válida sin recibos también se registra como actualización. |
| `ERROR_FUENTE` o `SUMINISTRO_NO_ENCONTRADO` | Conserva los datos y programa un reintento creciente. |
| `CONFLICTO` | Conserva los recibos aceptados y programa un reintento creciente. |
| `ERROR_ALMACENAMIENTO` o `ERROR_INTERNO` | Programa un reintento creciente cuando el calendario está disponible. |

Los reintentos crecen desde 30 segundos a 60, 120 y así sucesivamente
hasta 1800 segundos. El contador y las fechas se guardan en MySQL.
La tarea de recibos mantiene un calendario independiente del de lecturas.

| Propiedad bajo `nivora.recibos.sincronizacion` | Valor inicial |
|---|---:|
| `habilitada` | `true` |
| `espera-inicial-ms` | 5000 |
| `intervalo-ms` | 10000 |
| `maximo-intentos-por-ciclo` | 2 |
| `limite-recibos` | 60 |
| `reserva-segundos` | 60 |
| `intervalo-completo-segundos` | 300 |
| `reintento-inicial-segundos` | 30 |
| `reintento-maximo-segundos` | 1800 |

El intervalo completo es una espera mínima por suministro. La carga y los
otros intentos pendientes pueden retrasar la siguiente consulta.
`ultimo_exito_utc` indica el momento de una consulta exitosa a la fuente.
La fecha de disponibilidad del recibo y la de primera recepción en Nivora
se conservan en `recibos`.

## Preparar y comprobar en Windows

Desde `mysql>`, con la cuenta administradora y la tabla `recibos` ya creada,
aplica el script.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/13-sincronizacion-recibos.sql;
SHOW TABLES FROM nivora LIKE 'programacion_sincronizacion_recibos';
```

Compila con `mvnw.cmd clean verify`. Con Empresa de Agua en 8081, arranca
Nivora en modo web desde el CMD de `tecnologia\nivora-backend`. El siguiente
comando permite comprobar la tarea y pide la contraseña de `nivora_app`.

```bat
powershell -NoProfile -Command "$clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar'"
```

El mensaje `Sincronizacion de recibos` muestra los conteos del suministro
y el próximo intento. Si el recibo ya fue guardado, se cuenta como repetido.
Consulta el resultado persistido desde el cliente MySQL.

```sql
SELECT codigo_suministro, ultimo_resultado, fallos_consecutivos, ultimo_intento_utc, ultimo_exito_utc, proximo_intento_utc
FROM nivora.programacion_sincronizacion_recibos WHERE codigo_suministro = '13100390';
```

Las comprobaciones con `--spring.main.web-application-type=none` se
ejecutan de forma puntual. El perfil de pruebas desactiva las tareas
automáticas y usa una fuente HTTP controlada.
