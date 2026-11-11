# Gestion de administradores: bloque 3, parte 3

La API permite que una cuenta con `GESTIONAR_ADMINISTRADORES` agregue otras
personas, consulte cuentas y auditoria, asigne o retire ese permiso,
desactive o reactive cuentas y restablezca claves. Cada persona conserva
su propio usuario. Todas tienen rol `ADMIN`; gestionar otras cuentas es un
permiso adicional. No existe registro publico de administradores.

Esta parte implementa el backend. Las pantallas se incorporaran con la interfaz.
El acceso de consulta con suministro y DNI se mantiene independiente.

## Preparacion

Se utilizan las trece tablas existentes de los scripts 01-08. No hay un nuevo
script SQL, no se repite la inicializacion y no se modifica la clave que ya
tiene la primera cuenta. `nivora_app` conserva sus permisos DML existentes.

Detener el backend de Nivora con Ctrl+C en su CMD antes de reemplazar archivos.
Desde CMD, comprobar el proyecto:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

Iniciar el backend en ese CMD, introduciendo oculta la clave de MySQL:

```bat
powershell -NoProfile -Command "$claveNivora = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $claveNivora).Password; & '.\mvnw.cmd' spring-boot:run"
```

Mantener esa ventana abierta. La gestion de administradores usa la base propia;
no necesita consultar la Empresa de Agua.

## Rutas y autorizacion

Todas las operaciones protegidas reciben `Authorization: Bearer ...`. Las
respuestas usan `Cache-Control: no-store` y excluyen claves y hashes.
Los identificadores de auditoria y versiones se devuelven como texto decimal.

| Metodo y ruta | Funcion |
|---|---|
| `GET /api/v1/administracion/administradores` | Lista paginada de cuentas. |
| `GET /api/v1/administracion/administradores/{usuario}` | Datos de una cuenta. |
| `GET /api/v1/administracion/administradores/{usuario}/auditoria` | Eventos que afectaron a esa cuenta, ordenados por ID. |
| `POST /api/v1/administracion/administradores` | Alta de otra cuenta. |
| `PUT /api/v1/administracion/administradores/{usuario}/estado` | Activar o desactivar. |
| `PUT /api/v1/administracion/administradores/{usuario}/permisos` | Conceder o retirar gestion de administradores. |
| `PUT /api/v1/administracion/administradores/{usuario}/clave` | Restablecer la clave de otra persona. |
| `PUT /api/v1/acceso/administradores/sesion/clave` | Cambiar la propia clave; admite una sesion con cambio pendiente. |

Las primeras siete rutas requieren `GESTIONAR_ADMINISTRADORES`. La ultima
requiere una sesion administrativa valida y la clave actual de esa misma
persona, aunque todavia no tenga permisos por cambio obligatorio.

Las dos listas aceptan `limite`, entre 1 y 100, con valor inicial 20.
`despuesDe` del listado es un usuario canonico, inicialmente vacio.
`despuesDe` de auditoria es un ID decimal canonico no negativo, inicialmente
`0`. Se devuelve `siguienteDespuesDe` y `hayMas`; para continuar se utiliza
ese cursor. El listado vacio devuelve cursor nulo; auditoria vacia conserva
el cursor solicitado. No representan una instantanea entre paginas.

## Cuerpos JSON

Solo se acepta `application/json` UTF-8, hasta 4096 bytes. Los campos de texto
y booleanos son estrictos: `false` es un booleano, `"false"` no lo es.
Se rechazan campos adicionales, campos repetidos y contenido despues del
objeto. El actor se obtiene de la sesion autenticada, nunca del cuerpo.

| Operacion | Campos requeridos | Opcionales |
|---|---|---|
| Alta | `usuario`, `nombre`, `claveInicial`, `claveActual` | `gestionarAdministradores`, falso si se omite. |
| Estado | `activa`, `claveActual` | Ninguno. |
| Permiso | `gestionarAdministradores`, `claveActual` | Ninguno. |
| Restablecimiento | `claveTemporal`, `claveActual` | Ninguno. |
| Clave propia | `claveActual`, `claveNueva` | Ninguno. |

En altas, cambios de estado, permisos y restablecimientos, `claveActual` es
la clave del administrador que esta realizando la operacion. No es la de
la persona afectada. En el cambio propio, es la de esa misma cuenta.

Las claves iniciales, temporales y nuevas tienen entre 15 y 128 caracteres
Unicode, sin controles. Conservan exactamente sus espacios y caracteres.
La excepcion local del bootstrap no habilita claves cortas en estas altas.
El cambio propio rechaza volver a utilizar la clave actual. El usuario
es inmutable, se normaliza a minusculas y conserva las reglas 3-50 ASCII.
El nombre se recorta en los extremos y admite 1-120 caracteres Unicode.

El alta devuelve 201, `Location` con la cuenta canonica y este resultado:

```json
{
  "cuenta": {
    "usuario": "operador.agua",
    "nombre": "Persona de ejemplo",
    "activa": true,
    "gestionarAdministradores": false,
    "cambioClaveRequerido": true,
    "versionCredenciales": "1",
    "creadoEn": "2026-11-11T01:00:00Z",
    "actualizadoEn": "2026-11-11T01:00:00Z"
  },
  "modificada": true,
  "sesionActualRevocada": false
}
```

Los otros cambios devuelven 200 con la misma estructura. Si el valor de
estado o permiso ya era el solicitado, `modificada` es falso: no se cambia
la version, no se revocan sesiones y no se registra otro evento.

## Recorrido de una nueva persona

1. El gestor inicia sesion y crea una cuenta con una clave inicial entregada
   a esa persona por un medio privado. Por defecto no concede gestion.
2. La persona inicia sesion con su propio usuario y la clave inicial.
3. Con `cambioClaveRequerido=true`, puede consultar su sesion, cambiar su
   clave o cerrar sesion. Supervision y gestion responden 403.
4. Envia el cambio propio con la clave actual y su nueva clave. El backend
   elimina la obligacion y revoca todas sus sesiones anteriores.
5. Inicia sesion otra vez con la nueva clave. Recibe supervision y, si se
   lo concedieron expresamente, gestion de administradores.

Un restablecimiento por otro gestor repite ese recorrido con una nueva clave
temporal. No se permite restablecer la propia cuenta mediante la ruta de
otra persona; se utiliza el cambio propio. Desactivar no elimina la cuenta
ni su auditoria. Reactivar no revive tokens anteriores.

## Transacciones, ultimo gestor y sesiones

Argon2 verifica la clave actual y protege las claves nuevas fuera de la
transaccion de escritura. Antes de confirmar el cambio se vuelven a
comprobar la sesion, caducidad, estado, permiso, version y hash de la cuenta
actora dentro de la transaccion. Una confirmacion antigua no autoriza una
mutacion si la cuenta, la clave o la sesion cambiaron mientras tanto.

La fila existente `control_administradores` serializa las mutaciones, incluso
entre conexiones distintas. Cada cambio efectivo de estado, permiso o clave
incrementa `version_credenciales` y revoca todas las sesiones de la cuenta
afectada. Cuenta, revocaciones y auditoria se confirman juntos. Un fallo SQL
revierte esas escrituras y devuelve 503. Las solicitudes posteriores con
tokens anteriores responden 401; no se emite un token sustituto en el cambio.

Debe permanecer al menos un gestor utilizable: cuenta activa, con permiso
de gestion y sin cambio de clave pendiente. Una segunda cuenta con clave
temporal todavia no satisface esa condicion. Retirar el permiso o desactivar
el ultimo gestor devuelve 409. Dos solicitudes concurrentes tampoco pueden
eliminar ese ultimo acceso. Con otro gestor utilizable, una persona puede
retirar su propio permiso o desactivarse; su respuesta indica
`sesionActualRevocada=true`.

La inicializacion no se utiliza para recuperar, reactivar o restablecer
cuentas. Tampoco hay borrado de cuentas, cambio de usuario ni recuperacion
publica de claves en esta parte.

## Auditoria y limites

| Operacion registrada | Significado |
|---|---|
| `CREACION_ADMINISTRADOR` | Alta posterior realizada por un gestor autenticado. |
| `CONCESION_GESTION_ADMINISTRADORES` | Permiso concedido, incluso si se concede durante el alta. |
| `REVOCACION_GESTION_ADMINISTRADORES` | Permiso retirado. |
| `ACTIVACION_ADMINISTRADOR` | Reactivacion. |
| `DESACTIVACION_ADMINISTRADOR` | Desactivacion. |
| `RESTABLECIMIENTO_CLAVE` | Otra persona asigno una clave temporal. |
| `CAMBIO_CLAVE` | La persona cambio su propia clave. |

Se conservan `CREACION_INICIAL`, `INICIO_SESION` y `CIERRE_SESION`. Cada evento
guarda actor, afectado, operacion y fecha UTC; no incluye claves ni tokens.

Las confirmaciones de clave tienen contadores persistidos separados de los
inicios de sesion. Se utiliza la misma ventana de 60 segundos. Bajo
`nivora.administradores`, `confirmaciones-por-usuario=10` y
`confirmaciones-por-origen=50` cuentan todas las confirmaciones, incluidas
las correctas. Superar el limite devuelve 429 con `Retry-After`. No se
confia en cabeceras del cliente para identificar su origen.

| Respuesta | Situacion |
|---|---|
| 400 | Datos o cursor invalidos, clave nueva corta o igual a la actual. |
| 401 | Falta sesion valida o la clave actual es incorrecta. |
| 403 | Falta permiso de gestion o hay cambio inicial pendiente. |
| 404 | Cuenta solicitada inexistente. |
| 409 | Usuario duplicado, ultimo gestor o restablecimiento de la propia cuenta. |
| 413 / 415 | Cuerpo demasiado grande o tipo de contenido incompatible. |
| 429 | Demasiadas confirmaciones. |
| 503 | Fallo al consultar o confirmar los datos propios. |

## Consulta desde un CMD libre

Con el backend iniciado, este comando pide la clave oculta de `sach2003`,
lista cuentas, consulta su auditoria y cierra la sesion. No modifica cuentas
ni imprime el token:

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $clave = Read-Host 'Clave administrativa de sach2003' -AsSecureString; $cuerpo = @{usuario='sach2003'; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $inicio = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8082/api/v1/acceso/administradores/sesiones' -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($cuerpo)); $cabeceras = @{Authorization='Bearer '+$inicio.token}; try { Write-Host 'CUENTAS'; Invoke-RestMethod -Uri 'http://127.0.0.1:8082/api/v1/administracion/administradores' -Headers $cabeceras | ConvertTo-Json -Depth 6; Write-Host 'AUDITORIA'; Invoke-RestMethod -Uri 'http://127.0.0.1:8082/api/v1/administracion/administradores/sach2003/auditoria' -Headers $cabeceras | ConvertTo-Json -Depth 6 } finally { Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8082/api/v1/acceso/administradores/sesion' -Headers $cabeceras } }"
```

En `mysql>`, estas consultas excluyen claves y hashes:

```sql
SELECT usuario, nombre, activa, gestionar_administradores, cambio_clave_requerido, version_credenciales FROM nivora.administradores ORDER BY usuario;
```

```sql
SELECT id_evento, actor_usuario, afectado_usuario, operacion, registrado_en_utc FROM nivora.auditoria_administradores ORDER BY id_evento DESC LIMIT 20;
```

## Validacion

Se agregan 43 pruebas con HTTP real, H2 y reloj controlable. Cubren el
recorrido de alta, cambio obligatorio, permisos, estado, restablecimiento,
revocacion de dispositivos, confirmaciones de clave y sus limites, JSON
estricto, paginacion, errores, rollback de auditoria y dos gestores
concurrentes. Las 315 pruebas anteriores siguen pasando: total 358, sin
fallos, errores ni omisiones.

Tambien se comprobo con MySQL 8.0.46 y Spring HTTP real, usando una cuenta
con permisos SELECT, INSERT, UPDATE y DELETE. Se verificaron la clave corta
ya existente, altas de claves normales, cambios obligatorios, permisos,
desactivacion, reactivacion, restablecimiento, cierre de sesiones anteriores,
paginacion y auditoria. Dos retiradas concurrentes de gestion conservaron
un gestor utilizable. Las trece tablas y las lecturas existentes se conservaron.

## Referencias

- [OWASP: autorizacion](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
- [OWASP: autenticacion y cambios sensibles](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
- [OWASP: gestion de sesiones](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
