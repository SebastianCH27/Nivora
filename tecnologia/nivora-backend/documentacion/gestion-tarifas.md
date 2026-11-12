# Gestión administrativa de tarifas

La gestión se aplica a suministros registrados en Nivora. Las cuentas
administrativas activas que ya establecieron su clave definitiva tienen
el permiso `GESTIONAR_TARIFAS`.

| Método y ruta | Operación |
|---|---|
| `GET /api/v1/administracion/suministros/{codigo}/tarifas` | Consulta el historial importado, la última publicación y el número de revisión administrativa. Si aún no hay publicaciones, la revisión administrativa es `"0"`. |
| `PUT /api/v1/administracion/suministros/{codigo}/tarifas` | Publica una asignación en la revisión administrativa actual. Devuelve el calendario de la revisión y el usuario que la publicó. |
| `GET /api/v1/administracion/suministros/{codigo}/tarifas/revisiones/{revision}` | Consulta una publicación anterior, con su fecha UTC, usuario y asignaciones. |

## Publicar una vigencia

Envía la `revisionEsperada` como cadena con el número que devolvió el GET.
Si otro administrador publica primero, la API devuelve HTTP 409 y se
puede revisar el calendario actualizado. Reenviar la misma configuración
sobre la revisión actual devuelve esa revisión.

Los tramos y precios usan m³ y PEN. La fecha aplicable de inicio es la
mayor entre `vigenteDesde` y `asignadaDesde`. `asignadaHasta` es
exclusiva. Un valor `null` o ausente indica vigencia sin fecha final.
Al publicar, el nuevo intervalo reemplaza las asignaciones administrativas
dentro de ese intervalo. Los tramos de fechas que quedan fuera se conservan.
Para cambiar los precios o las condiciones de una versión, usa otro
número de versión. Las versiones publicadas se conservan inmutables.

Este cuerpo JSON ilustra una publicación de demostración para enero de
2099. Sustituye `revisionEsperada` por el valor consultado. La publicación
queda guardada para el suministro que aparezca en la URL.

```json
{
  "revisionEsperada": "0",
  "codigoTarifa": "DEMO-GESTION",
  "versionTarifa": 1,
  "nombreTarifa": "Tarifa de demostracion",
  "vigenteDesde": "2099-01-01",
  "asignadaDesde": "2099-01-01",
  "asignadaHasta": "2099-02-01",
  "cargoFijo": 5.00,
  "igvPorcentaje": 18,
  "tramos": [
    { "desdeM3": 0, "hastaM3": 10, "precioAguaPorM3": 1.50, "precioAlcantarilladoPorM3": 0.50 },
    { "desdeM3": 10, "hastaM3": null, "precioAguaPorM3": 2.20, "precioAlcantarilladoPorM3": 0.80 }
  ]
}
```

Los tramos deben comenzar en cero, ser consecutivos y terminar con un
tramo sin tope. El cargo fijo admite dos decimales, los límites de volumen
admiten tres y los precios y el IGV admiten cuatro. Si un concepto no
está informado, se puede omitir o enviar como `null` para alcantarillado
o IGV. Un cero expresa un valor conocido.

## Aplicación en las estimaciones

Si una asignación administrativa coincide con todo o parte del mes, Nivora
busca una asignación administrativa que cubra el mes completo. Si el mes
queda dividido entre tarifas, responde `SIN_TARIFA_COMPATIBLE`. Para los
meses sin una vigencia administrativa, usa el historial de Empresa de Agua.
Las importaciones posteriores siguen guardándose y las publicaciones
administrativas conservan su vigencia. Ambos historiales tienen números de
revisión independientes.

## Comprobación en Windows

Ejecuta el script con una cuenta de MySQL autorizada para crear tablas.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/11-gestion-tarifas.sql;
SHOW TABLES FROM nivora LIKE 'tarifas_publicaciones';
```

Compila con `mvnw.cmd clean verify`. Arranca el nuevo JAR de Nivora y,
en otro CMD, consulta las revisiones. El comando pide las credenciales,
devuelve el historial y cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $seguro = Read-Host 'Clave administrativa' -AsSecureString; $datos = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/acceso/administradores/sesiones') -ContentType 'application/json' -Body $datos -TimeoutSec 30; $cabeceras = @{Authorization=('Bearer ' + $inicio.token)}; try { Invoke-RestMethod -UseBasicParsing -Uri ($base + '/administracion/suministros/13100390/tarifas') -Headers $cabeceras -TimeoutSec 30 | ConvertTo-Json -Depth 9 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/acceso/administradores/sesion') -Headers $cabeceras -TimeoutSec 30 | Out-Null } }"
```
