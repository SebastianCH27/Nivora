# Consulta administrativa de suministros y accesos

La primera parte del bloque 8 implementa RF31. Lee los suministros que
Nivora ha verificado y las aperturas de sesión de consulta registradas
desde el script 09. Los cierres y las expiraciones conservan el historial.

Las rutas requieren una sesión administrativa válida con `CONSULTAR_ACCESOS`.
Este permiso se concede a las cuentas administrativas activas que hayan
completado su cambio de clave. El token se envía en la cabecera
`Authorization: Bearer ...`. Las respuestas incluyen `Cache-Control: no-store`.

## Rutas

| Ruta | Resultado |
|---|---|
| `GET /api/v1/administracion/suministros` | Lista de suministros reconocidos por Nivora, ordenada por código. Incluye el titular y `verificadoEn`. |
| `GET /api/v1/administracion/suministros/{codigo}/accesos` | Historial de accesos exitosos de consulta del suministro, del más reciente al más antiguo. |

Ambas rutas aceptan `limite` de 1 a 100, con valor predeterminado 20.
Para pasar de página, copia el valor de `siguienteDespuesDe` en
`despuesDe` en el listado de suministros, o el de `siguienteAntesDe`
en `antesDe` en el historial. `hayMas: false` indica el final del listado.
Los códigos se tratan como texto para conservar los ceros iniciales.
Los accesos de la misma fecha se ordenan por el identificador de sesión.

Cada registro del historial muestra `dniEnmascarado`, con los dos últimos
dígitos visibles, y los instantes UTC `creadaEn`, `expiraEn` y `revocadaEn`.
El historial omite el DNI completo, el nombre del consultante y las
credenciales. `revocadaEn` es nulo si la sesión carece de una revocación
registrada.

| Estado | Significado |
|---|---|
| HTTP 200 | Página de resultados. Un suministro registrado sin accesos tiene una lista vacía. |
| HTTP 400 | Código, cursor o límite inválido. |
| HTTP 401 | Falta una sesión administrativa válida. |
| HTTP 403 | La sesión carece del permiso requerido. |
| HTTP 404 | El suministro indicado carece de un registro local. |
| HTTP 503 | El almacenamiento de registros está indisponible. |

## Comprobación en Windows

Usa el backend compilado con `mvnw.cmd clean verify` y la base MySQL de Nivora.
La comprobación consulta un suministro registrado con tu cuenta administrativa.
En otro CMD, pega esta línea y escribe las credenciales cuando las solicite.
El comando cierra su sesión y muestra únicamente el código, la cantidad
de accesos mostrados y sus fechas.

```cmd
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $secreto = Read-Host 'Clave administrativa' -AsSecureString; $datos = @{usuario=$usuario;clave=[System.Net.NetworkCredential]::new('', $secreto).Password} | ConvertTo-Json -Compress; $api = 'http://127.0.0.1:8082/api/v1'; $sesion = Invoke-RestMethod -Method Post -Uri ($api + '/acceso/administradores/sesiones') -ContentType 'application/json' -Body $datos -TimeoutSec 30; $h = @{Authorization='Bearer ' + $sesion.token}; try { $lista = Invoke-RestMethod -Uri ($api + '/administracion/suministros?limite=1') -Headers $h -TimeoutSec 30; Write-Host ('Suministros en la pagina: ' + @($lista.suministros).Count); $codigo = Read-Host 'Codigo de suministro registrado'; $historial = Invoke-RestMethod -Uri ($api + '/administracion/suministros/' + $codigo + '/accesos?limite=5') -Headers $h -TimeoutSec 30; Write-Host ('Accesos registrados para ' + $historial.codigoSuministro + ': ' + @($historial.accesos).Count); $historial.accesos | Select-Object creadaEn,expiraEn,revocadaEn } finally { Invoke-RestMethod -Method Delete -Uri ($api + '/acceso/administradores/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```

Con un código ya usado para consultar Nivora, el historial devuelve las
sesiones exitosas, incluidas las ya vencidas o cerradas.
