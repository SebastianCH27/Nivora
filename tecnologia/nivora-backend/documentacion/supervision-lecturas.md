# API local de supervision de lecturas

El endpoint consulta los datos propios de un suministro registrado en Nivora:

```text
GET /api/v1/supervision/suministros/{codigo}/lecturas
```

Requiere una sesion administrativa y el permiso `SUPERVISAR_INTEGRACION`.
El token se envia exclusivamente en el encabezado `Authorization: Bearer ...`.
El cliente de [Sesiones administrativas](sesiones-administrativas.md) permite
probar el acceso manteniendo el token en memoria, sin publicarlo.

No consulta a la Empresa de Agua, no ejecuta recuperaciones, no adelanta
reintentos y no escribe lecturas, incidencias ni progreso. Utiliza una sola
sentencia de lectura consistente de las tablas InnoDB. Los agregados estan
filtrados por fuente y suministro, con los indices existentes. La tarea
programada continua siendo la responsable de obtener y guardar las novedades.

## Preparacion y prueba desde Windows

Se necesitan los scripts 01-06 ya aplicados; este avance no incorpora cambios
de estructura. Nivora conserva sus ocho tablas. Desde CMD, en
`C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend`, comprobar:

```bat
mvnw.cmd clean verify
```

Si Nivora sigue ejecutando una version anterior, detener su proceso en la
ventana del puerto 8082 con Ctrl+C. Iniciar la version actual desde ese CMD:

```bat
powershell -NoProfile -Command "$claveNivora = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $claveNivora).Password; & '.\mvnw.cmd' spring-boot:run"
```

Este arranque normal reemplaza tambien la opcion de registro DEBUG utilizada
durante el diagnostico anterior. Mantener abierta la consola del servidor.
Desde otro CMD libre, comprobar que el acceso sin sesion responde 401:

```bat
curl.exe -i "http://127.0.0.1:8082/api/v1/supervision/suministros/13100390/lecturas"
```

Para las respuestas de negocio 200, 400 y 404, utilizar una sesion valida
mediante el cliente autenticado indicado arriba. El codigo `99999999` debe
estar ausente de la base propia para esperar 404.
El servicio esta vinculado a `127.0.0.1`, como el resto del entorno local.
Este endpoint tecnico utiliza la proteccion administrativa del bloque de
acceso; el inicio de sesion de consulta con suministro y DNI sigue pendiente.

## Contrato HTTP

| Caso | Respuesta |
|---|---|
| Sin sesion administrativa valida | 401, ProblemDetail de sesion requerida o no valida. |
| Sesion sin permiso de supervision | 403, ProblemDetail `Permiso insuficiente`. |
| Suministro registrado en Nivora | 200 con la instantanea, incluso si todavia no hay lecturas. |
| Codigo fuera de 1-20 digitos ASCII | 400, ProblemDetail `Solicitud invalida`. |
| Codigo valido sin registro propio | 404, ProblemDetail `Suministro no registrado`. |
| No se puede consultar la base propia o hay estados guardados incompatibles | 503, ProblemDetail `Supervision no disponible`. |

Las respuestas GET del contrato incluyen `Cache-Control: no-store`.
Los errores incluyen `status`, `title`, `detail` e `instance`. No incluyen
SQL, causas internas, cuerpos del proveedor ni credenciales. El endpoint
no ofrece operaciones de escritura por POST.

Un `ERROR_FUENTE` guardado no convierte la consulta de supervision en 503:
si la base propia responde, devuelve 200 con el fallo que conserva. De esta
forma sigue siendo consultable durante una caida de la Empresa. El 404
describe exclusivamente ausencia en Nivora, no inexistencia confirmada en
la Empresa ni una autorizacion de acceso.

## Respuesta

Ejemplo ilustrativo de un recorrido terminado:

```json
{
  "fuente": "empresa-agua",
  "codigoSuministro": "13100390",
  "consultadoEn": "2026-11-10T23:00:00.123456Z",
  "sincronizacionAutomaticaHabilitada": true,
  "almacenamiento": {
    "lecturasGuardadas": 2,
    "primeraMedicion": "2026-10-01T05:00:00Z",
    "ultimaMedicion": "2026-10-02T05:00:00Z",
    "ultimaRecepcion": "2026-11-10T22:00:00Z",
    "incidenciasLecturas": 0,
    "incidenciasRecuperacionAbiertas": 0
  },
  "recuperacion": {
    "estado": "RECORRIDO_COMPLETO",
    "cursorConfirmado": "100391",
    "revision": "5",
    "ultimaConfirmacion": "2026-11-10T22:00:00Z",
    "actualizadoEn": "2026-11-10T22:00:00Z",
    "corte": {
      "hastaIdLectura": "4076585",
      "confirmadoHasta": "2026-11-10T21:59:00Z",
      "capturadoEn": "2026-11-10T22:00:00Z"
    }
  },
  "programacion": {
    "ultimoResultado": "RECORRIDO_COMPLETO",
    "fallosConsecutivos": 0,
    "ultimoIntento": "2026-11-10T22:00:00Z",
    "ultimoExito": "2026-11-10T22:00:00Z",
    "proximoIntento": "2026-11-10T22:05:00Z",
    "reservaHasta": null,
    "reservaVigente": false,
    "reservaVencida": false
  }
}
```

| Campo | Significado |
|---|---|
| `consultadoEn` | Instante UTC utilizado como referencia al iniciar esta consulta; no es una actualizacion del proveedor. |
| `sincronizacionAutomaticaHabilitada` | Configuracion del proceso web que atiende la consulta. No prueba que una fuente externa este disponible ni que todos los trabajadores funcionen. |
| `lecturasGuardadas` | Cantidad de filas aceptadas para esta fuente y suministro; no es consumo. |
| `primeraMedicion` / `ultimaMedicion` | Minimo y maximo de las fechas de mediciones aceptadas. |
| `ultimaRecepcion` | Ultima fecha de primera incorporacion entre las lecturas guardadas; reconocer una repetida no la adelanta. |
| `incidenciasLecturas` | Filas de incidencias de lectura conservadas, incluidas las historicas; no suma el contador de repeticiones ni representa rechazos del ultimo intento. |
| `incidenciasRecuperacionAbiertas` | Tipos de incidencia de fuente que permanecen abiertos para este suministro; excluye los resueltos. |
| `recuperacion` | Progreso persistido. Es `null` si aun no existe; su ausencia no equivale a un recorrido completo. |
| `corte` | Limite y fecha confirmados por la fuente para el recorrido guardado. Es `null` si el progreso todavia no tiene contexto. |
| `programacion` | Calendario automatico persistido. Es `null` si aun no se ha preparado. |
| `ultimoResultado` | Resultado del ultimo intento automatico finalizado y confirmado. Puede ser `null` antes del primer intento. |
| `ultimoIntento` | Fecha guardada al reservar el intento mas reciente; puede ser posterior al `ultimoResultado` si aun esta en proceso. |
| `ultimoExito` | Ultimo recorrido automatico completo; no es la fecha de medicion ni `confirmadoHasta`. |
| `proximoIntento` | Fecha minima de elegibilidad, sujeta al calendario y la reserva. Un calendario pausado la deja en `null`. |
| `reservaHasta` | Vencimiento de la reserva, sin exponer su token. |
| `reservaVigente` | Hay una reserva cuyo vencimiento es posterior a `consultadoEn`; no acredita que el proceso propietario siga vivo. |
| `reservaVencida` | Hay una reserva que vence en o antes de `consultadoEn`; podra ser recuperada por la tarea programada. |

La reserva no sustituye el ultimo resultado: `ERROR_FUENTE` junto con una
reserva vigente conserva el fallo anterior mientras el intento reservado
todavia no confirma su resultado. Una reserva vencida se muestra; la consulta
no la libera ni ejecuta su recuperacion. Si el trabajador esta deshabilitado,
la API sigue mostrando el calendario y los resultados guardados.

Las fechas son instantes UTC ISO-8601 con sufijo `Z`, con la precision
almacenada. Los codigos, cursores, IDs de corte y revisiones son texto para
conservar ceros iniciales y enteros superiores a la precision exacta de
JavaScript. El titular, las lecturas individuales, el inventario JSON,
los datos de rechazos y los tokens de reserva quedan fuera de este resumen.

Un suministro sin lecturas tiene conteo cero y fechas de almacenamiento
nulas. Recuperacion y programacion pueden existir independientemente.
Un fallo de lectura de la base no se reemplaza por ceros ni por un 404.

`RECORRIDO_COMPLETO` confirma que termino la paginacion de su corte sin
rechazos; no certifica actualidad hasta este instante ni continuidad temporal
de las mediciones. El corte es global a la Empresa y puede superar el ultimo
ID del suministro: esa diferencia no es un porcentaje ni una cantidad de
lecturas faltantes. La cobertura temporal y los calculos de consumo requieren
sus reglas posteriores.

## Validacion

Las 20 pruebas de supervision se conservan y ahora preparan una sesion
administrativa. Con los incrementos de cuentas y sesiones, la suite suma 315.
Utilizan un servidor Spring real con puerto temporal y H2
con los scripts 02-06. Cubren respuestas vacias, filtros por fuente y codigo,
ceros iniciales, IDs grandes, fechas UTC, incidencias abiertas, reserva vigente
y vencida, programacion pendiente, bloqueo, errores y consultas repetidas sin
modificar ninguna tabla. Un proveedor HTTP de prueba responde 503 y cuenta
sus solicitudes; las consultas de supervision no realizan ninguna.

Se comprobo tambien con MySQL 8.0.46 y las dos aplicaciones Spring reales:
recorridos automaticos, nuevas publicaciones, caida y restablecimiento de
la Empresa y reinicio de Nivora conservando el calendario. Durante la caida,
la API devuelve las lecturas propias y los fallos guardados. Una escritura
de lecturas y progreso sin confirmar en otra conexion no bloquea la consulta
ni aparece en su instantanea. Al recuperarse la fuente, la API muestra las
nuevas lecturas, el corte del recorrido y el contador de fallos restablecido.
