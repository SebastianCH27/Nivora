# Configuración de periodos mensuales

El calendario mensual de Nivora usa `America/Lima`. El día inicial
predeterminado es 1. Cada publicación fija un día de inicio para el mes
indicado en `desde` y para los meses siguientes hasta otra regla vigente.
El día puede ir de 1 a 31. Si el mes tiene menos días, se usa el último día
de ese mes.

El periodo se identifica por el mes en el que empieza. Cada periodo comienza
a medianoche en Lima y termina al comenzar el siguiente. Nivora devuelve
esos instantes en UTC. Por ejemplo, si diciembre de 2026 comienza el día 5,
el periodo de noviembre se extiende hasta el 5 de diciembre a medianoche.
Este tramo de transición puede tener una duración distinta de un mes civil.

El consumo acumulado, el historial, las comparaciones, las estimaciones,
las proyecciones y las alertas usan este calendario. Los recibos conservan
el periodo y el importe emitidos por Empresa de Agua. La consulta de
recibos expone las dos ventanas y el indicador `coincideConPeriodoAnalisis`.

## API administrativa

Se requiere `GESTIONAR_PERIODOS` para consultar y publicar en
`/api/v1/administracion/periodos-mensuales`.

| Método | Resultado |
| --- | --- |
| `GET` | Revisión vigente de la configuración, periodo actual y reglas por mes de entrada en vigor. |
| `PUT` | Publicación de una regla con su propia revisión, fecha y usuario responsable. |

Cada publicación usa como `revisionEsperada` la cadena recibida en
`revision` del último `GET`. La revisión inicial es `"0"`.
`desde` debe corresponder a un mes civil futuro en Lima. Una publicación
posterior puede actualizar una regla futura del mismo mes y conserva las
revisiones previas en MySQL. Los periodos ya cerrados conservan sus límites.

Ejemplo de cuerpo para `PUT` si diciembre de 2026 todavía es futuro y
el calendario está en revisión inicial.

```json
{"revisionEsperada":"0","desde":"2026-12","diaInicio":5}
```

`400` indica una fecha, día o cuerpo inválido. `401` indica que hace falta una
sesión administrativa válida y `403` que falta el permiso. Ante `409`, vuelve a
consultar la revisión antes de publicar. `503` indica un fallo de almacenamiento
del calendario. Las respuestas usan `Cache-Control: no-store`.

## Comprobación en Windows

Con el usuario administrador de MySQL y las tablas administrativas ya
instaladas, ejecuta en `mysql>` el script de calendario antes de arrancar el JAR.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/20-periodos-mensuales.sql;
SHOW TABLES FROM nivora LIKE '%periodos_mensuales';
```

Con el backend en `8082`, abre otro CMD y ejecuta la consulta. Pide las
credenciales, muestra la configuración actual y cierra la sesión.

```cmd
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $clave = Read-Host 'Clave administrativa' -AsSecureString; $body = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $api = 'http://127.0.0.1:8082/api/v1'; $acceso = $api + '/acceso/administradores'; $inicio = Invoke-RestMethod -Method Post -Uri ($acceso + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $h = @{Authorization='Bearer ' + $inicio.token}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($api + '/administracion/periodos-mensuales') -Headers $h -TimeoutSec 30; Write-Host ('Periodos HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5 } finally { Invoke-RestMethod -Method Delete -Uri ($acceso + '/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```
