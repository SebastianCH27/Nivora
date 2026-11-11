# Cuentas administrativas: primera inicializacion

Primer incremento del bloque 3. Prepara el almacenamiento de cuentas,
su permiso de gestion, las credenciales protegidas y la creacion explicita
de la primera cuenta. El acceso con suministro y DNI conserva su diseno
independiente. El [inicio de sesion administrativo](sesiones-administrativas.md)
ya utiliza estas cuentas; las operaciones para gestionar otras cuentas
se implementaran en los siguientes incrementos.

## Modelo

| Dato | Regla |
|---|---|
| `usuario` | Entre 3 y 50 caracteres ASCII; comienza con letra y admite letras, numeros, punto, guion y guion bajo. Se guarda en minusculas con `Locale.ROOT`. |
| `nombre` | Entre 1 y 120 caracteres Unicode, sin controles; se eliminan espacios exteriores del nombre mostrado. |
| `activa` | Estado de la cuenta; se comprobara durante el acceso y la autorizacion posteriores. |
| `gestionar_administradores` | Permiso para gestionar otras cuentas. Por defecto es falso; la primera cuenta lo recibe explicitamente. |
| `cambio_clave_requerido` | Preparado para el cambio de claves iniciales de altas posteriores. La primera cuenta elige su propia clave y se crea con este valor falso. |
| `version_credenciales` | Comienza en 1; permitira invalidar sesiones cuando se implementen cambios de credenciales y estado. |
| `creado_en_utc` / `actualizado_en_utc` | Fechas UTC con microsegundos. |

`Administrador`, el modelo devuelto por el caso de uso y el repositorio,
no contiene la clave ni el hash. Cada cuenta tiene su propio usuario;
`ADMIN.Nivora` y `admin.nivora` identifican la misma cuenta.

El permiso de gestionar cuentas no se deduce de un DNI ni del titular de
un suministro. La primera cuenta se prepara mediante el procedimiento local
de configuracion; las cuentas siguientes requeriran un administrador
autorizado mediante el flujo que se implementara despues.

## Proteccion de claves

Se utiliza `spring-security-crypto` administrado por Spring Boot y
Bouncy Castle `bcprov-jdk18on` 1.86. `ProtectorClavesArgon2` configura
Argon2id con 19456 KiB de memoria, dos iteraciones, paralelismo 1,
sal aleatoria de 16 bytes y salida de 32 bytes. Cada proteccion genera
una sal diferente. No se utiliza cifrado reversible para guardar la clave.

La clave nueva admite entre 15 y 128 puntos de codigo Unicode, incluidos
espacios. No se recorta, no se normaliza y no exige combinaciones de tipos
de caracteres. Rechaza caracteres de control y secuencias UTF-16 invalidas.
La comprobacion utiliza solo hashes del formato y parametros previstos;
un valor con costos arbitrarios de CPU o memoria se rechaza sin ejecutarlo.
Una evolucion de estos parametros debera incorporar compatibilidad y pruebas
de los formatos existentes. Los parametros iniciales requieren sus mediciones
de capacidad junto con el flujo de autenticacion posterior.

Para una inicializacion local de demostracion se puede habilitar
`nivora.administradores.permitir-clave-corta-inicial=true`, que admite desde
6 caracteres. Por defecto es falso. La configuracion rechaza esta excepcion
en un servidor web y tambien fuera de la inicializacion expresamente
habilitada. No modifica el formato Argon2id ni los datos existentes.
Una clave numerica de seis digitos es facil de adivinar y debe cambiarse
antes de un despliegue real. El valor de la clave no se guarda en archivos.

La comprobacion de un hash existente no aplica el minimo de las altas nuevas:
la clave ya guardada puede verificarse en un arranque normal, incluso si se
creo mediante la excepcion local. Cambiar la configuracion de altas no cambia
por si solo una clave existente ni la vuelve mas fuerte.

Referencias tecnicas utilizadas:

- [OWASP: almacenamiento de contrasenas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
- [OWASP: controles de contrasenas](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
- [Spring Security: almacenamiento de contrasenas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- [Bouncy Castle Java](https://www.bouncycastle.org/download/bouncy-castle-java/).

## Preparar MySQL

Con los scripts 01-06 aplicados, abrir MySQL como root desde un CMD libre:

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

En `mysql>`, ejecutar:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/07-administradores.sql;
SHOW TABLES FROM nivora;
```

Se agregan tres tablas, para un total de once:

| Tabla | Responsabilidad |
|---|---|
| `administradores` | Cuentas, hash de la clave, estado y permiso de gestion. |
| `control_administradores` | Fila de coordinacion para serializar inicializaciones y futuros cambios. |
| `auditoria_administradores` | Actor, cuenta afectada, operacion y fecha; no guarda claves ni hashes. |

El script puede repetirse y conserva las cuentas y eventos anteriores.
La cuenta `nivora_app` utiliza los permisos DML ya asignados sobre `nivora.*`;
el backend no ejecuta cambios de estructura.

## Preparar la primera cuenta

Desde CMD, en `C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend`,
ejecutar el siguiente comando. Pedira usuario, nombre, clave de MySQL y
dos veces la nueva clave administrativa. Las claves se introducen ocultas;
elige una clave administrativa propia de al menos 15 caracteres.

```bat
powershell -NoProfile -Command "$env:NIVORA_ADMIN_USUARIO = Read-Host 'Usuario administrativo'; $env:NIVORA_ADMIN_NOMBRE = Read-Host 'Nombre del administrador'; $claveBD = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $claveBD).Password; $claveAdmin = Read-Host 'Nueva clave administrativa (15 a 128 caracteres)' -AsSecureString; $confirmacion = Read-Host 'Repite la nueva clave administrativa' -AsSecureString; $env:NIVORA_ADMIN_CLAVE = [System.Net.NetworkCredential]::new('', $claveAdmin).Password; $confirmacionTexto = [System.Net.NetworkCredential]::new('', $confirmacion).Password; if ($env:NIVORA_ADMIN_CLAVE -cne $confirmacionTexto) { throw 'Las claves administrativas no coinciden.' }; & '.\mvnw.cmd' spring-boot:run '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.administradores.inicializar-al-iniciar=true'; exit $LASTEXITCODE"
```

El programa no abre un puerto HTTP y finaliza despues de la operacion.
No necesita que la Empresa de Agua este ejecutandose. Las variables son
del proceso de PowerShell y sus procesos hijos, no del CMD padre ni del
perfil permanente de Windows. El inicializador lee `NIVORA_ADMIN_USUARIO`,
`NIVORA_ADMIN_NOMBRE` y `NIVORA_ADMIN_CLAVE` del entorno del proceso;
no acepta esos valores como argumentos de arranque o propiedades del archivo.

El mensaje esperado es:

```text
Administracion inicial preparada: usuario ...; cuenta activa true; permiso gestionar administradores true.
BUILD SUCCESS
```

El usuario no distingue mayusculas; la clave conserva exactamente sus
caracteres y espacios. Las claves no deben pegarse en el chat, en el codigo
ni en comandos que contengan sus valores literales.

## Una sola inicializacion y auditoria

La proteccion de la clave se completa antes de abrir la transaccion SQL.
El repositorio bloquea la fila de control y comprueba que no haya ninguna
cuenta. Inserta la cuenta y el evento `CREACION_INICIAL` en la misma
transaccion. El actor de este evento es `NULL`, representando la preparacion
local inicial. Si falla la auditoria, revierte tambien la cuenta.

Dos inicializadores simultaneos se coordinan mediante la fila de control:
solo uno puede crear la primera cuenta. La comprobacion cuenta tambien
cuentas inactivas. Repetir el procedimiento no sirve para agregar otra cuenta,
reactivar, cambiar permisos ni restablecer una clave existente. El caso de uso
rechaza la repeticion; el comprobador informa que ya se inicializo y termina
sin cambios. Las operaciones posteriores utilizaran sus casos autorizados.

La propiedad `nivora.administradores.inicializar-al-iniciar` vale `false`
por defecto. Solo activa el comprobador si se establece expresamente en
`true` y la aplicacion es no web. Un servidor web no lo activa aunque reciba
esa propiedad. No existe un endpoint publico para crear la primera cuenta.

Para usar la excepcion local, el argumento
`--nivora.administradores.permitir-clave-corta-inicial=true` debe agregarse
al grupo `spring-boot.run.arguments` del comando anterior, manteniendo tanto
la ejecucion no web como `inicializar-al-iniciar=true`. El campo oculto de
la clave sigue utilizandose; no se coloca su valor literal en el comando.

## Comprobar el resultado

Estas consultas, en una sola linea cada una, pueden ejecutarse en `mysql>`:

```sql
SELECT usuario, nombre, activa, gestionar_administradores, cambio_clave_requerido, version_credenciales, creado_en_utc FROM nivora.administradores;
```

```sql
SELECT id_evento, actor_usuario, afectado_usuario, operacion, registrado_en_utc FROM nivora.auditoria_administradores;
```

Se espera una cuenta activa con permiso de gestion, cambio de clave requerido
en falso y version 1; ademas de un evento `CREACION_INICIAL`. Estas consultas
excluyen el hash de la clave.

## Validacion y siguientes incrementos

La suite incorpora 33 pruebas nuevas, para un total de 284. Cubren Argon2id
real, sal aleatoria, limites Unicode, espacios, claves incorrectas, hashes
incompatibles, usuarios canonicos, transacciones, auditoria, concurrencia,
reaplicacion del script y activacion exclusivamente no web y explicita.
Incluyen la excepcion local de clave corta, su rechazo en un servidor web
y la comprobacion posterior del hash con la politica normal.
Las 251 pruebas anteriores siguen pasando.

Se comprobo ademas con MySQL 8.0.46 y el inicializador Spring real, usando
una cuenta de ejecucion con SELECT, INSERT, UPDATE y DELETE. Se verificaron
la primera cuenta y su permiso, el hash real, la auditoria, un segundo
arranque sin cambios, la reaplicacion del SQL y la conservacion de las lecturas.
La excepcion local tambien se comprobo con una clave ficticia de seis
digitos, verificando su hash mediante el protector con politica normal.

El inicio de sesion administrativo y la proteccion de supervision estan
descritos en [Sesiones administrativas](sesiones-administrativas.md).
La suite general con las 31 pruebas nuevas de sesiones contiene 315 pruebas.
Se incorporaran las operaciones para altas adicionales, cambio o
restablecimiento de claves, permisos y desactivacion. Estos datos base no
suponen que esos endpoints o las pantallas ya esten implementados.
