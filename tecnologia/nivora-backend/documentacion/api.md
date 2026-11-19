# Guia de la API de Nivora

Bloque 9, parte 4. Contrato revisado sobre el codigo de `4fd156a`.
La API de negocio tiene 40 operaciones explicitas. Se documenta ademas
`GET /actuator/health`. Las rutas de la Empresa de Agua son contratos externos
y se describen en su propio modulo.

La especificacion [openapi.json](openapi.json) usa OpenAPI 3.1.0 e incluye
metodos, parametros, cuerpos, respuestas, errores, permisos y ejemplos
ficticios. Se puede importar como archivo en herramientas compatibles con
OpenAPI 3.1. Esta version es documental y se conserva en el repositorio;
no publica un endpoint nuevo de documentacion en la aplicacion.

## Direccion y formatos

Servidor local: `http://127.0.0.1:8082`. Las rutas del inventario son completas;
no agregar otro `/api/v1` delante. La ejecucion en GitHub de las pruebas de
carga es temporal y no constituye una URL de produccion para el frontend.

| Elemento | Contrato |
|---|---|
| Respuestas de negocio con cuerpo | `application/json` |
| Errores tratados por controladores y seguridad | `application/problem+json` |
| Respuestas 204 | Sin cuerpo; no intentar analizarlas como JSON |
| Cache de las operaciones de negocio | `Cache-Control: no-store` |
| Cuerpos de escritura | `application/json`, UTF-8 si se declara charset, objeto completo de hasta 4096 bytes |
| Campos del cuerpo | Requeridos y opcionales segun OpenAPI; sin adicionales, duplicados ni contenido tras el objeto |
| Codigo de suministro y DNI | Cadenas; conservar ceros iniciales. El codigo tiene de 1 a 20 digitos y el DNI exactamente 8 |
| Instantes | ISO 8601 UTC, con `Z`; la precision depende del dato y del reloj |
| Fechas civiles | `AAAA-MM-DD`; periodos `AAAA-MM`; zona de analisis `America/Lima` |
| Importes de consulta, litros y revisiones | Cadenas cuando el DTO asi lo define; no convertirlos a flotante al conservar precision |
| Publicacion y lectura administrativa de tarifas | `cargoFijo`, tasas, precios y limites de tramos son numeros JSON; las revisiones siguen siendo cadenas |
| Valores `null` | Ausencia o calculo no disponible segun estado; no reemplazarlos automaticamente por cero |

Los tipos numericos enteros de las publicaciones se envian sin fraccion ni
exponente. Un literal `1.0` puede ser rechazado por el lector aunque sea
matematicamente entero. Las fechas de ejemplo deben adaptarse al momento
de uso: las reglas de periodos solo se publican para meses civiles futuros.

## Dos sesiones Bearer distintas

| Sesion | Inicio | Credenciales | Alcance |
|---|---|---|---|
| Consulta | `POST /api/v1/acceso/consulta/sesiones` | `codigoSuministro` y `dni` | Un suministro fijado en el inicio |
| Administrativa | `POST /api/v1/acceso/administradores/sesiones` | `usuario` y `clave` | Operaciones administrativas segun los permisos de la cuenta |

Ambos inicios devuelven 201 con `tipoToken`, `token` y `sesion`. Las llamadas
posteriores usan `Authorization: Bearer <token_devuelto>`. Son tokens opacos,
no JWT: el de consulta tiene prefijo `nc_` y 43 caracteres base64url; el
administrativo tiene 43 caracteres base64url, sin ese prefijo. Se validan
en el servidor y no se intercambian entre los dos grupos de rutas.

Las consultas del suministro toman el codigo de la sesion. Agregar
`codigoSuministro` a la query no selecciona otro suministro; los contratos
de aislamiento comprueban que ese parametro no cambia el alcance.

La duracion predeterminada de consulta es 1800 segundos. La administrativa
expira por 900 segundos de inactividad o 28800 segundos de duracion maxima.
Estos valores son configurables; usar las fechas devueltas en la sesion.
Consultar una sesion administrativa renueva su actividad dentro de sus limites.

`DELETE .../sesion` devuelve 204 al revocar una sesion vigente. Descartar el
token al cerrar, vencer o recibir 401. Como la revocacion invalida el token,
repetir el cierre con ese token puede responder 401.

Una cuenta con `cambioClaveRequerido=true` inicia con permisos vacios. Puede
consultar/cerrar su sesion y cambiar su propia clave; debe actualizarla antes
de usar funciones protegidas por un permiso. Las respuestas de gestion indican
`sesionActualRevocada`: si vale true, se necesita iniciar otra sesion.

## Permisos administrativos

| Permiso | Operaciones |
|---|---|
| `ROLE_ADMIN` | Consultar/cerrar la propia sesion y cambiar su clave |
| `GESTIONAR_ADMINISTRADORES` | Listar/consultar cuentas y auditoria; crear cuentas, cambiar estado, permiso y clave temporal |
| `CONSULTAR_ACCESOS` | Listar suministros y sesiones de consulta con DNI enmascarado |
| `GESTIONAR_TARIFAS` | Consultar historial/revisiones y publicar tarifas |
| `GESTIONAR_PERIODOS` | Consultar calendario y publicar reglas futuras |
| `GESTIONAR_ALERTAS` | Consultar o modificar la evaluacion de alertas de consumo |
| `SUPERVISAR_INTEGRACION` | Consultar supervision de lecturas/recibos e incidencias |
| `REINTENTAR_INTEGRACION` | Programar reintentos de lecturas o recibos |

`rol` es `ADMIN`; las facultades concretas estan en `sesion.permisos`.
Actualmente el permiso de gestionar cuentas depende de
`gestionarAdministradores`; las cuentas sin cambio de clave pendiente
reciben los otros permisos definidos por el backend. Las escrituras de cuentas
requieren ademas `claveActual` del actor. Se conserva al menos un gestor
activo y sin cambio de clave pendiente; los conflictos se comunican con 409.

## Inventario de operaciones

Los valores de la columna Permiso se refieren a la sesion administrativa,
salvo `CONSULTA`, que requiere una sesion de consulta, y `PUBLICO`, que no
requiere autenticacion. Las restricciones de estados y de negocio estan
detalladas en la descripcion de cada operacion de OpenAPI.

| Metodo | Ruta | Permiso | Exito | Esquema de respuesta |
|---|---|---|---:|---|
| POST | `/api/v1/acceso/consulta/sesiones` | `PUBLICO` | 201 | `InicioConsulta` |
| GET | `/api/v1/acceso/consulta/sesion` | `CONSULTA` | 200 | `SesionConsulta` |
| DELETE | `/api/v1/acceso/consulta/sesion` | `CONSULTA` | 204 | Sin cuerpo |
| POST | `/api/v1/acceso/administradores/sesiones` | `PUBLICO` | 201 | `InicioAdministrativo` |
| GET | `/api/v1/acceso/administradores/sesion` | `ADMIN` | 200 | `SesionAdministrativa` |
| DELETE | `/api/v1/acceso/administradores/sesion` | `ADMIN` | 204 | Sin cuerpo |
| PUT | `/api/v1/acceso/administradores/sesion/clave` | `ADMIN` | 200 | `ResultadoGestionCuenta` |
| GET | `/api/v1/administracion/administradores` | `GESTIONAR_ADMINISTRADORES` | 200 | `PaginaAdministradores` |
| POST | `/api/v1/administracion/administradores` | `GESTIONAR_ADMINISTRADORES` | 201 | `ResultadoGestionCuenta` |
| GET | `/api/v1/administracion/administradores/{usuario}` | `GESTIONAR_ADMINISTRADORES` | 200 | `CuentaAdministrativa` |
| GET | `/api/v1/administracion/administradores/{usuario}/auditoria` | `GESTIONAR_ADMINISTRADORES` | 200 | `PaginaAuditoria` |
| PUT | `/api/v1/administracion/administradores/{usuario}/estado` | `GESTIONAR_ADMINISTRADORES` | 200 | `ResultadoGestionCuenta` |
| PUT | `/api/v1/administracion/administradores/{usuario}/permisos` | `GESTIONAR_ADMINISTRADORES` | 200 | `ResultadoGestionCuenta` |
| PUT | `/api/v1/administracion/administradores/{usuario}/clave` | `GESTIONAR_ADMINISTRADORES` | 200 | `ResultadoGestionCuenta` |
| GET | `/api/v1/acceso/consulta/consumo-actual` | `CONSULTA` | 200 | `ConsumoActual` |
| GET | `/api/v1/acceso/consulta/historial` | `CONSULTA` | 200 | `HistorialMensual` |
| GET | `/api/v1/acceso/consulta/estadisticas` | `CONSULTA` | 200 | `EstadisticasConsumo` |
| GET | `/api/v1/acceso/consulta/costos` | `CONSULTA` | 200 | `CostosConsumo` |
| GET | `/api/v1/acceso/consulta/proyeccion` | `CONSULTA` | 200 | `ProyeccionMensual` |
| GET | `/api/v1/acceso/consulta/recibos` | `CONSULTA` | 200 | `PaginaRecibos` |
| GET | `/api/v1/acceso/consulta/notificaciones` | `CONSULTA` | 200 | `PaginaNotificaciones` |
| PUT | `/api/v1/acceso/consulta/notificaciones/{id}/leida` | `CONSULTA` | 204 | Sin cuerpo |
| GET | `/api/v1/acceso/consulta/notificaciones/dispositivos/{id}` | `CONSULTA` | 200 | `PreferenciaDispositivo` |
| PUT | `/api/v1/acceso/consulta/notificaciones/dispositivos/{id}` | `CONSULTA` | 200 | `PreferenciaDispositivo` |
| GET | `/api/v1/acceso/consulta/notificaciones/web-push/clave-publica` | `CONSULTA` | 200 | `ClavePublicaVapid` |
| PUT | `/api/v1/acceso/consulta/notificaciones/dispositivos/{id}/suscripcion` | `CONSULTA` | 204 | Sin cuerpo |
| DELETE | `/api/v1/acceso/consulta/notificaciones/dispositivos/{id}/suscripcion` | `CONSULTA` | 204 | Sin cuerpo |
| GET | `/api/v1/administracion/suministros` | `CONSULTAR_ACCESOS` | 200 | `PaginaSuministros` |
| GET | `/api/v1/administracion/suministros/{codigo}/accesos` | `CONSULTAR_ACCESOS` | 200 | `PaginaAccesos` |
| GET | `/api/v1/administracion/suministros/{codigo}/tarifas` | `GESTIONAR_TARIFAS` | 200 | `VistaTarifaria` |
| GET | `/api/v1/administracion/suministros/{codigo}/tarifas/revisiones/{revision}` | `GESTIONAR_TARIFAS` | 200 | `PublicacionTarifaria` |
| PUT | `/api/v1/administracion/suministros/{codigo}/tarifas` | `GESTIONAR_TARIFAS` | 200 | `PublicacionTarifaria` |
| GET | `/api/v1/administracion/periodos-mensuales` | `GESTIONAR_PERIODOS` | 200 | `CalendarioMensual` |
| PUT | `/api/v1/administracion/periodos-mensuales` | `GESTIONAR_PERIODOS` | 200 | `ReglaMensual` |
| GET | `/api/v1/administracion/notificaciones/alertas-consumo` | `GESTIONAR_ALERTAS` | 200 | `ControlAlertas` |
| PUT | `/api/v1/administracion/notificaciones/alertas-consumo` | `GESTIONAR_ALERTAS` | 200 | `ControlAlertas` |
| GET | `/api/v1/supervision/suministros/{codigo}/lecturas` | `SUPERVISAR_INTEGRACION` | 200 | `SupervisionLecturas` |
| GET | `/api/v1/supervision/suministros/{codigo}/recibos` | `SUPERVISAR_INTEGRACION` | 200 | `SupervisionRecibos` |
| GET | `/api/v1/supervision/suministros/{codigo}/incidencias` | `SUPERVISAR_INTEGRACION` | 200 | `PaginaIncidencias` |
| POST | `/api/v1/supervision/suministros/{codigo}/{recurso}/reintentos` | `REINTENTAR_INTEGRACION` | 202 | `ReintentoProgramado` |

`GET /actuator/health` es publico y responde 200 o 503 segun la salud. Su
contenido puede ser `application/vnd.spring-boot.actuator.v3+json` o
`application/json`; los componentes dependen de la configuracion.
La especificacion lista los metodos explicitos de negocio; los metodos que
el framework atienda implicitamente se evalúan segun su comportamiento HTTP.
Los archivos de la pagina Web Push son recursos estaticos, no operaciones REST.

## Paginacion

Cada familia tiene su propio cursor. Reutilizar exactamente el valor devuelto
y codificarlo como parametro de query. Omitirlo para la primera pagina; un
cursor `null` en una respuesta indica que no hay siguiente pagina.

| Recurso | Parametros y valores iniciales | Cursor siguiente |
|---|---|---|
| Administradores | `limite=20`, rango 1 a 100; `despuesDe` vacio u omitido | `siguienteDespuesDe`, usuario exclusivo |
| Auditoria de cuenta | `limite=20`, rango 1 a 100; `despuesDe=0` | `siguienteDespuesDe`, ID numerico como cadena |
| Suministros | `limite=20`, rango 1 a 100; omitir `despuesDe` | `siguienteDespuesDe`, codigo exclusivo |
| Accesos del suministro | `limite=20`, rango 1 a 100; omitir `antesDe` | `siguienteAntesDe`, instante UTC y UUID separados por `~` |
| Recibos | `limite=12`, rango 1 a 60; omitir `antesDe` | `siguienteAntesDe`, periodo `AAAA-MM` exclusivo |
| Notificaciones | `limite=20`, rango 1 a 60; `soloPendientes=false`; omitir `antesDe` | `siguienteAntesDe`, instante UTC e ID de notificacion separados por `~` |
| Incidencias | `limite=20`, rango 1 a 100; omitir `antesDe` | `siguienteAntesDe`, instante, origen, referencia y fuente separados por `~` |

Los cursores compuestos de accesos, notificaciones e incidencias requieren
instante UTC con hasta seis decimales. No son intercambiables. Los IDs de
notificacion tienen forma `RECIBO_DISPONIBLE-AAAA-MM` o `ALERTA_CONSUMO-AAAA-MM`;
los IDs de dispositivo son UUID canonicos en minusculas.

Historial y estadisticas usan `meses=12` de forma predeterminada, con rango de
1 a 12. Historial acepta `hasta`, un periodo anterior al actual, y devuelve
los periodos mas recientes primero. Costos usa `meses=3`, tambien de 1 a 12.

## Estados, importes y operaciones particulares

- Un consumo puede ser `SIN_DATOS`, `PARCIAL`, `COMPLETO` o `INCOMPATIBLE`.
  Conservar `motivo` y los campos null cuando no se puede calcular.
- Los costos son estimaciones en PEN. Su estado puede ser
  `SIN_CONSUMO_CALCULABLE`, `SIN_TARIFA_COMPATIBLE`, `CONCEPTOS_INCOMPLETOS`
  o `ESTIMADO`; los conceptos faltantes no se completan con cero.
- La proyeccion exige al menos un dia observado con cobertura compatible.
  Expone sus supuestos y diferencia `PROYECTADO`, `COBERTURA_INCOMPLETA`,
  `TIEMPO_INSUFICIENTE`, `PERIODO_CERRADO` y `SIN_CONSUMO_CALCULABLE`.
- El recibo emitido conserva su importe y mes civil. `periodoFacturado` y
  `periodoAnalisis` hacen visible si coincide con el calendario de analisis.
- Marcar una notificacion leida es idempotente respecto de su primera fecha:
  repetir la marca conserva `leidaEn`. No requiere cuerpo JSON.
- La preferencia habilitada de un dispositivo requiere permiso `CONCEDIDO`.
  El navegador obtiene ese permiso por su propio flujo; guardarlo en Nivora
  no invoca el dialogo del navegador.
- La clave publica VAPID tambien requiere sesion de consulta. Si Web Push no
  esta configurado, la consulta de clave y la suscripcion pueden responder 503.
- Registrar Web Push requiere preferencia activa y claves validas. Un 409 puede
  indicar preferencia inactiva o endpoint ocupado. Retirar la suscripcion
  devuelve 204 aunque la asociacion de ese suministro y dispositivo ya falte.
- `POST .../{recurso}/reintentos` admite `lecturas` o `recibos` y no requiere
  cuerpo. Un 202 significa que quedo programado; consultar la ruta de
  supervision indicada por `Location` para observar el resultado posterior.
- Las publicaciones de tarifas y periodos usan una revision esperada como
  cadena. Ante 409, volver a consultar el estado y revisar el cambio antes
  de enviarlo otra vez. Las reglas mensuales comienzan en un mes civil futuro.

## Errores y cabeceras

| HTTP | Significado y accion del cliente |
|---:|---|
| 400 | Revisar campos, tipos, parametros y reglas; repetir el mismo cuerpo no corrige el problema |
| 401 | Iniciar la sesion correcta o revisar sus credenciales; incluye `WWW-Authenticate: Bearer` |
| 403 | La sesion esta autenticada pero carece del permiso requerido |
| 404 | Recurso ausente o fuera del alcance del suministro; no revela datos de otro suministro |
| 409 | Resolver la revision, reserva, asociacion o regla en conflicto antes de reintentar |
| 413 | Reducir el cuerpo JSON completo a un maximo de 4096 bytes |
| 415 | Enviar `Content-Type: application/json`, con UTF-8 si se declara charset |
| 429 | Esperar los segundos de `Retry-After` antes de repetir el acceso o la confirmacion administrativa |
| 503 | Servicio o datos necesarios no disponibles; las consultas locales pueden seguir funcionando cuando falla solo la fuente externa |

Cada operacion enumera en OpenAPI los errores tratados que le corresponden;
la tabla anterior es comun y no significa que todos se produzcan en cada ruta.
Errores adicionales generados por el framework, por ejemplo por un metodo o
Accept no admitidos, no deben confundirse con una respuesta funcional exitosa.

Ejemplo ficticio del filtro de seguridad:

```json
{
  "status": 401,
  "title": "Sesion requerida",
  "detail": "Inicia una sesion de consulta.",
  "instance": "/api/v1/acceso/consulta/consumo-actual"
}
```

Los errores de los controladores pueden incluir `type: about:blank`.
El consumidor no debe exigir esa propiedad cuando la respuesta viene del filtro.

## Ejemplo de consulta desde Windows

Con los servicios locales disponibles, abrir PowerShell desde un CMD:

```bat
powershell -NoProfile
```

Pegar el bloque siguiente en PowerShell. Solicita los datos de forma
interactiva, mantiene el token dentro del proceso y cierra la sesion al
terminar. El codigo y el DNI deben corresponder a un suministro accesible
en el entorno donde se ejecute; los ejemplos de OpenAPI son ficticios.

```powershell
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'
$inicio = $null
try {
    $codigo = Read-Host 'Codigo de suministro'
    $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString
    $body = @{
        codigoSuministro = $codigo
        dni = [System.Net.NetworkCredential]::new('', $seguro).Password
    } | ConvertTo-Json -Compress
    $inicio = Invoke-RestMethod -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30
    $cabecera = @{ Authorization = 'Bearer ' + $inicio.token }
    $consumo = Invoke-RestMethod -Method Get -Uri ($base + '/consumo-actual') -Headers $cabecera -TimeoutSec 30
    $consumo | ConvertTo-Json -Depth 10
} finally {
    try {
        if ($null -ne $inicio) {
            Invoke-RestMethod -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null
        }
    } finally {
        Remove-Variable body, inicio, cabecera, seguro -ErrorAction SilentlyContinue
    }
}
```

No colocar el DNI, claves o tokens reales en la especificacion, archivos de
ejemplo, URL, captura de logs o commit. El frontend utiliza el token de su
sesion y no incluye credenciales de MySQL, proveedores de identidad o VAPID.

## Verificacion y mantenimiento

Los [controles de documentacion](verificacion-documentacion-api.md) comparan
las 40 operaciones de negocio con los mappings registrados por Spring,
sus parametros y valores predeterminados, y los campos/tipos de los registros
y enums enlazados mediante `x-java-types` y `x-java-enum`.

Cuando cambie un contrato, actualizar OpenAPI, esta guia y la documentacion
especifica del modulo. Las reglas que dependen de datos y transiciones de
estado requieren sus pruebas funcionales; OpenAPI no sustituye esa validacion.

## Documentos por modulo

- [Sesiones de consulta](sesiones-consulta.md) y [sesiones administrativas](sesiones-administrativas.md).
- [Cuentas administrativas](gestion-administradores.md) y [registros de acceso](registros-acceso.md).
- [Consumo actual](consumo-actual.md), [historial](historial-mensual.md) y [estadisticas](estadisticas-consumo.md).
- [Costos estimados](costos-estimados.md), [proyeccion](proyeccion-mensual.md) y [gestion tarifaria](gestion-tarifas.md).
- [Recibos](consulta-recibos.md) y [periodos mensuales](periodos-mensuales.md).
- [Bandeja](bandeja-notificaciones.md), [configuracion de avisos](configuracion-notificaciones.md) y [Web Push](web-push.md).
- [Supervision de lecturas](supervision-lecturas.md), [recibos](supervision-recibos.md), [incidencias](incidencias-integracion.md) y [reintentos](reintentos-integracion.md).

Referencia del formato: [OpenAPI Specification 3.1.0](https://spec.openapis.org/oas/v3.1.0).
