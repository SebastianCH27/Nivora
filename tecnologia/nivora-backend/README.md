# Backend de Nivora

Implementacion del backend propio con Java 21, Spring Boot 4.1.2 y MySQL.
Se desarrolla como monolito modular, con dependencias organizadas segun
Clean Architecture.

## Servicios del entorno local

| Componente | Puerto | Base de datos |
|---|---|---|
| Empresa de Agua | 8081 | `empresa_agua` |
| Backend de Nivora | 8082 | `nivora` |

El puerto 8080 esta ocupado por Jenkins en el equipo de desarrollo.

## Modulos de negocio

| Paquete previsto | Modulo | Estado |
|---|---|---|
| `acceso` | Acceso y suministros | Suministros, consulta de DNI mediante APIsPERU, sesiones de consulta y administrativas, gestion de cuentas y supervision protegida. |
| `lecturas` | Lecturas, consumo e historial | Recuperacion manual y automatica con corte e inventario persistidos y API local de supervision; calculos pendientes. |
| `tarifas` | Tarifas, costos y proyecciones | Pendiente de implementar. |
| `notificaciones` | Alertas y notificaciones | Pendiente de implementar. |
| `recibos` | Recibos | Pendiente de implementar. |

Las capas de cada modulo se incorporaran cuando se implementen sus
responsabilidades: dominio, aplicacion, infraestructura y presentacion.
El dominio no depende de Spring, JDBC ni clientes HTTP.

## Primer contrato: consulta de suministros

Los archivos se encuentran en
`src/main/java/pe/nivora/backend/acceso/dominio/`.

| Tipo | Responsabilidad |
|---|---|
| `CodigoSuministro` | Validar entre 1 y 20 digitos ASCII y conservar los ceros iniciales. |
| `Suministro` | Representar el codigo y el titular asociado al suministro. |
| `FuenteSuministros` | Definir la consulta externa necesaria durante la validacion de un nuevo acceso. |
| `FuenteSuministrosNoDisponibleException` | Distinguir una incidencia externa de un suministro inexistente. |

El contrato devuelve un suministro cuando la fuente lo encuentra y un
`Optional` vacio cuando la fuente confirma que no existe. Un fallo de
comunicacion o una respuesta no utilizable debe producir la excepcion
de fuente no disponible; no debe convertirse en un resultado inexistente.

Este contrato corresponde a la comprobacion del suministro durante
un nuevo acceso. La consulta de DNI tiene su propio contrato.
El inicio de sesion y el registro del acceso utilizan ambos contratos.
El titular del suministro y la persona que consulta pueden ser diferentes.

El adaptador HTTP implementa este contrato en infraestructura.
Las consultas del panel utilizaran datos aceptados en el almacenamiento
propio de Nivora; no dependeran de una llamada a la Empresa por cada
solicitud del usuario.

### Adaptador de la Empresa de Agua

La API Java de la Empresa de Agua ofrece la consulta independiente
`GET /api/v1/suministros/{codigo}`, con `codigoSuministro` y `titular`,
ademas de las consultas de consumo actual, historial mensual, tarifa
vigente y recibos. `FuenteSuministrosHttp`, ubicado en
`acceso/infraestructura/empresaagua/`, implementa `FuenteSuministros`
mediante `RestClient`. Spring registra una instancia mediante
`EmpresaAguaConfiguracion`.

| Respuesta externa | Resultado del adaptador |
|---|---|
| HTTP 200, JSON con el mismo codigo y titular no vacio | Suministro encontrado. |
| HTTP 404, ProblemDetail del suministro solicitado con titulo `Suministro no encontrado` | Ausencia confirmada: `Optional.empty()`. |
| Otros estados, redirecciones, JSON invalido o datos incompatibles | Fuente no disponible. |
| Conexion rechazada o tiempo de espera agotado | Fuente no disponible. |

Los codigos se conservan como texto. No se siguen redirecciones y el
cuerpo de respuesta se limita a 64 KiB. Los mensajes de error propios
no incluyen el cuerpo recibido. Un 404 generico de una ruta incorrecta
no confirma que el suministro sea inexistente.

La configuracion esta en `application.properties`:

| Propiedad | Valor local | Significado |
|---|---|---|
| `nivora.empresa-agua.url-base` | `http://127.0.0.1:8081` | Origen HTTP del proveedor, sin ruta, credenciales ni parametros. |
| `nivora.empresa-agua.conexion-ms` | `3000` | Espera maxima para establecer la conexion. |
| `nivora.empresa-agua.lectura-ms` | `5000` | Espera maxima sin recibir datos durante una lectura de la respuesta. |
| `nivora.empresa-agua.comprobar-al-iniciar` | `false` | Activa solo la comprobacion manual por consola. |
| `nivora.empresa-agua.codigo-prueba` | `13100390` | Codigo utilizado por esa comprobacion. |

Estos tiempos son limites de red, no frecuencias de sincronizacion.
El arranque habitual no consulta a la Empresa de Agua.

## Consulta de DNI mediante APIsPERU

`FuenteIdentidad` recibe un `Dni` de ocho digitos ASCII y devuelve el
documento y el nombre completo informados por el proveedor. El adaptador
consulta `GET https://dniruc.apisperu.com/api/v1/dni/{numero}` con Bearer.
El DNI devuelto debe coincidir con el solicitado y la respuesta debe incluir
nombres. Los fallos de red, el limite de consultas y las respuestas sin
coincidencia se informan como una consulta no confirmada.

La credencial se lee de la variable de proceso `NIVORA_IDENTIDAD_API_TOKEN`.
La consulta manual usa `NIVORA_IDENTIDAD_DNI` y se activa con
`--nivora.identidad.comprobar-al-iniciar=true` en modo sin servidor web.
La comprobacion imprime solo el resultado de la consulta. El backend puede
arrancar con el token pendiente y el adaptador informa esa situacion al
intentar consultarlo. La consulta en APIsPERU informa datos asociados al
documento durante el inicio de sesion de consulta.

Desde CMD, ejecuta `mvnw.cmd clean verify`. Con el JAR generado, ejecuta
el siguiente comando. Al primer aviso, pulsa **Copiar** junto a **Mi Token**
en el panel de APIsPERU, vuelve a CMD y presiona Enter. Luego escribe el DNI
de prueba en el campo oculto.

```bat
powershell -NoProfile -STA -Command "& { $null = Read-Host 'Pulsa Copiar junto a Mi Token en el panel y vuelve aqui para presionar Enter' -AsSecureString; $env:NIVORA_IDENTIDAD_API_TOKEN = ([string](Get-Clipboard -Raw)).Trim(); $seguro = Read-Host 'Escribe tu DNI de 8 digitos' -AsSecureString; $env:NIVORA_IDENTIDAD_DNI = [System.Net.NetworkCredential]::new('', $seguro).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' --spring.main.web-application-type=none --spring.datasource.password= --nivora.sincronizacion.habilitada=false --nivora.identidad.comprobar-al-iniciar=true; exit $LASTEXITCODE }"
```

El resultado esperado es `Consulta DNI correcta desde Nivora mediante APIsPERU.`

## Inicio de sesion de consulta

Nivora verifica el suministro con la Empresa de Agua y el DNI con APIsPERU.
El acceso exitoso se registra en MySQL y genera una sesion de consulta con
vigencia de 30 minutos, limitada al suministro elegido. El script
`base-datos/09-sesiones-consulta.sql` agrega las dos tablas de este paso.
Las rutas y la prueba desde CMD estan en
[Sesiones de consulta](documentacion/sesiones-consulta.md).
La matriz de pruebas del bloque está en
[Verificación del acceso](documentacion/verificacion-acceso.md).

## Almacenamiento propio de suministros

`ActualizarSuministro`, en la capa de aplicacion, consulta `FuenteSuministros`
y guarda los datos encontrados mediante `RepositorioSuministros`. Las reglas
de aplicacion y los contratos no dependen de Spring, HTTP ni JDBC.

`RepositorioSuministrosJdbc` conserva en la tabla `nivora.suministros`:

| Columna | Informacion |
|---|---|
| `codigo` | Codigo textual, clave primaria; conserva los ceros iniciales. |
| `titular` | Titular informado por la fuente. |
| `verificado_en_utc` | Fecha de la verificacion realizada por Nivora, con microsegundos. |

La fecha corresponde a una verificacion del suministro, no a una lectura
de consumo ni a una version emitida por la Empresa. La tabla conserva el
ultimo registro aceptado por codigo, no un historial de verificaciones.

La escritura atomica inserta o actualiza el mismo codigo sin duplicarlo.
Una escritura con fecha anterior a la almacenada no reemplaza el registro.
Una consulta a `RepositorioSuministros.buscar` utiliza solo la base propia:
un resultado vacio significa que no hay datos guardados. Un fallo de lectura
o escritura produce `RepositorioSuministrosNoDisponibleException`.

Si la consulta externa falla o confirma que el suministro no existe, no
se borran ni se actualizan los datos previamente aceptados. Conservarlos
no autoriza un nuevo acceso: sus validaciones externas siguen siendo
obligatorias, segun ADR-003 y ADR-004.

### Crear la tabla en MySQL

Con una cuenta administradora, abrir el cliente MySQL desde CMD:

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

Dentro del cliente, cuando aparezca `mysql>`, ejecutar:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/02-suministros.sql;
SHOW CREATE TABLE nivora.suministros;
```

El script crea la tabla si no existe. La cuenta de ejecucion `nivora_app`
mantiene sus permisos de lectura y escritura sobre `nivora.*`; el backend
no ejecuta cambios de estructura automaticamente.

### Integracion pendiente

El backend incorpora la consulta externa de DNI y el almacenamiento propio
de suministros y lecturas. Las sesiones de consulta registran accesos exitosos.
Queda pendiente el almacenamiento de tarifas y recibos. La API local de
supervision de lecturas consulta el estado propio.

La Empresa de Agua incorpora `GET /api/v1/suministros/{codigo}/lecturas`,
con paginas de mediciones historicas identificadas, medidor, fecha UTC,
tipo acumulado y unidad en litros. Los detalles del cursor estan en su README.
Nivora implementa su adaptador y las validaciones de estructura y paginacion.
La incorporacion manual almacena lecturas, distingue repetidas y registra rechazos.
La recuperacion manual confirma el avance de cada recorrido conservando su
corte y una copia del inventario. La sincronizacion automatica reutiliza ese
proceso para los suministros propios con reintentos y programacion persistidos.

El generador continuo de la Empresa publica nuevas lecturas identificadas
junto con sus totales mensuales. La recuperacion manual de Nivora puede
incorporarlas utilizando los contratos existentes. La programacion de esa
recuperacion se conserva en MySQL y se ejecuta separada de las consultas,
segun ADR-003. La Empresa ofrece un corte
confirmado, el parametro opcional `hastaId` y el inventario de medidores.
Nivora consume esos datos mediante `FuenteLecturasConCorteHttp` y los guarda
en `contexto_recuperacion_lecturas` para reanudar el mismo recorrido.
Un acumulado mensual no equivale a un incremento nuevo que deba sumarse
cada vez que se consulta.

## Consulta de paginas de lecturas

El paquete `lecturas/dominio` define `LecturaAcumulada`, `LoteLecturas` y
el contrato `FuenteLecturas`. Se reutiliza `CodigoSuministro` como identificador
del suministro. Estos modelos no dependen de bibliotecas HTTP, JSON ni Spring.

`FuenteLecturasHttp`, en `lecturas/infraestructura/empresaagua`, consulta
una pagina y traduce sus datos al dominio. Recibir un lote no significa
que se haya incorporado a la base propia ni que su cursor este confirmado.

| Respuesta externa | Resultado |
|---|---|
| HTTP 200 con pagina compatible | Lote presente, incluso si no tiene lecturas. |
| HTTP 404 con ProblemDetail del suministro solicitado | `Optional.empty()`: ausencia confirmada. |
| Error de red, otro estado HTTP o respuesta incompatible | `FuenteLecturasNoDisponibleException`. |
| Cursor o limite solicitado invalido | `IllegalArgumentException`, sin consultar la fuente. |

El adaptador valida:

- Fuente `empresa-agua`, suministro solicitado, tipo `ACUMULADA` y unidad `L`.
- Identificadores decimales positivos como texto, sin perder digitos.
- Fechas en formato ISO de instante UTC con sufijo `Z`.
- Contadores enteros no negativos representables en `long`.
- Pagina de hasta el limite solicitado y sin mezclar el suministro indicado.
- ID estrictamente creciente y posterior al cursor, sin duplicados dentro de la pagina.
- Cursor final igual al ultimo ID recibido, o sin cambios si la pagina esta vacia.
- Indicador booleano `hayMas`; una pagina que anuncia mas filas debe estar completa.

Los cursores de este proveedor son decimales canonicos entre `0` y
`9223372036854775807`; el limite solicitado esta entre 1 y 500. Una pagina
incompatible con el contrato HTTP se rechaza completa. No se corrige silenciosamente ni se avanza
un cursor persistido desde este adaptador.

`ClienteEmpresaAgua`, en infraestructura compartida, concentra la conexion,
los tiempos de espera, la lectura JSON y el rechazo de redirecciones.
Los adaptadores mantienen separados sus resultados y excepciones de negocio.
El limite del cuerpo es de 64 KiB para suministros y 512 KiB para lecturas.

La incorporacion propia comprueba la fecha respecto de su recepcion, la
consistencia del contador y las coincidencias con registros ya aceptados.
La recuperacion principal valida los IDs respecto del corte confirmado y
las mediciones respecto de su fecha y del inventario persistido. Este avance
no calcula consumo ni trata cada contador como un incremento nuevo.

## Incorporacion de lecturas en la base propia

`ImportarPaginaLecturas` obtiene una pagina mediante `FuenteLecturas` y la
entrega a `RepositorioLecturas`. La llamada HTTP se completa antes de abrir
la transaccion de escritura. El suministro debe existir en `nivora.suministros`.
La consulta y el almacenamiento permanecen separados de las validaciones
del inicio de sesion.

`RepositorioLecturasJdbc` ejecuta cada lote de hasta 500 lecturas en una
transaccion. Las incorporaciones de una misma fuente se coordinan mediante
una fila de control bloqueada, incluso si utilizan conexiones distintas.
Las reglas de fecha y contador se encuentran en el dominio, en
`PoliticaIncorporacionLecturas`.

| Caso | Resultado |
|---|---|
| Fuente e ID nuevos, medicion compatible | Nueva lectura guardada. |
| Misma fuente e ID con todos los datos iguales | Repetida; conserva el registro y su primera recepcion. |
| Misma fuente e ID con otros datos | Rechazada; registra incidencia y conserva la lectura aceptada. |
| Otro ID para la misma fuente, suministro, medidor y fecha | Rechazada como medicion ya identificada. |
| Fecha fuera del rango 1000-9999, con precision superior a microsegundos o posterior a su recepcion | Rechazada y registrada en incidencias. |
| Contador inferior al anterior o superior al posterior del mismo medidor | Rechazada y registrada en incidencias. |
| Medicion posterior al corte, medidor ausente del inventario o dia fuera de sus fechas administrativas | Rechazada y registrada; conserva las otras lecturas validas y bloquea el avance. |
| Fallo de almacenamiento durante el lote | Revierte todas las escrituras del lote. |

La primera lectura compatible de un medidor se conserva como referencia,
sin asumir que su contador sea consumo del mes. Un medidor distinto puede
tener otro valor inicial. La fecha se guarda en UTC sin redondear la medicion.

El script `base-datos/03-lecturas.sql`, posterior a `02-suministros.sql`, crea:

| Tabla | Responsabilidad |
|---|---|
| `lecturas` | Datos aceptados, fuente, ID, suministro, medidor, fecha, contador, unidad y tipo. |
| `incidencias_lecturas` | Motivo y datos recibidos de cada rechazo. |
| `control_incorporacion_lecturas` | Coordinacion de escrituras por fuente; no contiene un cursor confirmado. |

La identidad de una lectura es fuente e ID. Una clave adicional evita dos
identificaciones de la misma medicion. Los identificadores distinguen
mayusculas de minusculas. Los contadores almacenados son acumulados y no
se suman para obtener el consumo.

Repetir el mismo rechazo no crea otra fila de incidencia: conserva los datos
recibidos y el motivo, e incrementa su contador de repeticiones. Un rechazo
con otros datos conserva su propia incidencia. Una lectura rechazada no
se incorpora a la tabla de lecturas aceptadas.

Los rechazos de estas reglas de incorporacion permiten conservar las otras
lecturas validas del lote junto con sus incidencias. Un fallo tecnico de la
transaccion revierte el lote entero. Si una lectura rechazada llega corregida,
puede aceptarse posteriormente; la incidencia anterior conserva su detalle.

La comprobacion contra la hora de recepcion se complementa con la fecha del
corte y los dias declarados en el inventario del proveedor. Las fechas de
instalacion y retiro no tienen precision horaria; la gestion completa de
sustituciones sigue pendiente. Aun faltan la gestion de fallos de comunicacion
para los otros procesos de integracion. Las lecturas ya tienen reintentos
programados con limites y fechas persistidos.
La Empresa ya genera mediciones identificadas de forma continua. La recuperacion
manual registra sus fallos de fuente y conserva el cursor de su recorrido.

## Recuperacion manual de paginas

`RecuperarLecturasConCorte`, utilizado por el comprobador principal, obtiene
el inventario y el corte fuera de la transaccion de escritura. Guarda ese
contexto junto con una nueva revision del progreso. Solicita cada pagina con
el mismo `hastaId`; los fallos conservan el contexto y el cursor confirmado.
Las lecturas, sus incidencias y el avance de pagina se confirman atomicamente.

Un recorrido terminado se revisa desde el inicio en la siguiente ejecucion.
Esto permite detectar despues IDs antiguos que hayan aparecido tarde o
cambiado, utilizando la deduplicacion existente, bajo un nuevo corte. El corte
acota altas confirmadas; no versiona las modificaciones historicas ni el
inventario. Un recorrido parcial no acredita haber procesado todo ese corte.

Antes de utilizar esta recuperacion, aplicar como administrador
`base-datos/05-contexto-recuperacion-lecturas.sql`, despues del script 04.
Conserva los datos anteriores. Un progreso sin contexto vuelve al cursor cero
al obtenerlo, para revisar las lecturas previas con las nuevas validaciones.

Las comprobaciones individuales de `FuenteLecturas` e `ImportarPaginaLecturas`
se conservan. La recuperacion principal no utiliza esas consultas sin limite
como alternativa cuando falta el corte o el inventario.

La preparacion de MySQL, los estados, los limites y las comprobaciones estan
en [Recuperacion de lecturas](documentacion/recuperacion-lecturas.md).

## Sincronizacion automatica

La tarea del servidor web usa la recuperacion con corte para los suministros
registrados en Nivora. Reserva un lote acotado, conserva el avance de paginas
y programa reintentos crecientes ante fallos. Los bloqueos de lecturas se
pausan para revision. Las reservas vencidas permiten recuperar el trabajo
tras un cierre, manteniendo la deduplicacion y las revisiones existentes.

Antes de iniciar el servidor con esta funcion habilitada, aplicar
`base-datos/06-sincronizacion-lecturas.sql`. El arranque web normal en 8082
activa la tarea; las comprobaciones no web siguen separadas. El perfil
`test` la desactiva.

La configuracion, los estados de programacion y las comprobaciones estan en
[Sincronizacion de lecturas](documentacion/sincronizacion-lecturas.md).

## API local de supervision de lecturas

`GET /api/v1/supervision/suministros/{codigo}/lecturas` consulta una instantanea
de las lecturas guardadas, incidencias, progreso, corte y programacion del
suministro propio. La respuesta distingue el ultimo resultado de una reserva
vigente o vencida. La consulta usa una sola sentencia de lectura de MySQL,
no llama a la Empresa y no modifica el calendario ni el cursor.
Requiere una sesion administrativa con permiso `SUPERVISAR_INTEGRACION`.
Sin sesion responde 401; con una sesion sin ese permiso responde 403.

Un suministro registrado sin lecturas devuelve 200 con conteo cero y fechas
nulas; la ausencia en Nivora devuelve 404. Un codigo invalido devuelve 400 y
un fallo al consultar los datos propios devuelve 503. Los errores de fuente
guardados se informan dentro de una respuesta 200 cuando la lectura propia
funciona. Las respuestas del endpoint usan `Cache-Control: no-store`.

No requiere otro script SQL: utiliza las ocho tablas de los scripts 01-06.
La API de supervision corresponde al entorno local vinculado a
`127.0.0.1`; utiliza las sesiones y permisos administrativos del bloque de acceso.
El contrato, las fechas y los ejemplos estan en
[Supervision de lecturas](documentacion/supervision-lecturas.md).

## Consumo acumulado del mes

La sesión de consulta permite leer el consumo de su suministro mediante
`GET /api/v1/acceso/consulta/consumo-actual`. El cálculo usa las lecturas
aceptadas en MySQL y el mes de `America/Lima`. La respuesta muestra
litros, m³, fechas, referencias, cobertura y estado de actualización.
El contrato y la prueba desde CMD están en
[Consumo acumulado del mes](documentacion/consumo-actual.md).

## Historial mensual de consumo

`GET /api/v1/acceso/consulta/historial` devuelve los meses anteriores del
suministro de la sesión con su cobertura. Usa `meses` para elegir hasta
12 periodos consecutivos y `hasta` para consultar ventanas más antiguas.
Consulta los parámetros, los estados y la prueba desde CMD en
[Historial mensual de consumo](documentacion/historial-mensual.md).

## Comparación y estadísticas de consumo

`GET /api/v1/acceso/consulta/estadisticas` compara el acumulado actual con
el mes anterior e indica la duración medida de cada tramo. Devuelve una
serie cronológica para el gráfico y un resumen de los meses completos.
Consulta el contrato y la prueba desde CMD en
[Comparación y estadísticas](documentacion/estadisticas-consumo.md).
La matriz de pruebas del bloque está en
[Verificación del consumo](documentacion/verificacion-consumo.md).

## Tarifas y vigencias de la Empresa de Agua

La fuente simulada expone el historial tarifario del suministro con las
fechas de aplicación de cada versión y sus tramos en soles por m³.
El contrato de la fuente se describe en
[Historial tarifario por suministro](../empresa-agua/README.md#historial-tarifario-por-suministro).
Nivora valida y conserva las revisiones por suministro. El script y la
prueba desde CMD están en
[Incorporación de tarifas](documentacion/almacenamiento-tarifas.md).

## Cuentas administrativas: primer paso del bloque 3

`InicializarAdministracion` prepara la primera cuenta activa, con permiso
para gestionar administradores, y su evento de auditoria en la misma
transaccion. Las claves nuevas se protegen con Argon2id y sal aleatoria.
Los datos de cuenta que devuelve el repositorio excluyen la clave y su hash.

Aplicar como administrador `base-datos/07-administradores.sql`, despues del
script 06. Agrega `administradores`, `control_administradores` y
`auditoria_administradores`; Nivora pasa a tener once tablas.
La inicializacion requiere una ejecucion no web expresamente habilitada
y variables de proceso introducidas por consola. Repetirla conserva las
cuentas existentes, incluso si estan inactivas. El arranque web no la activa.

El inicio de sesion administrativo utiliza un token Bearer aleatorio cuya
huella y vigencia se guardan en MySQL. El servidor verifica estado de cuenta,
version de credenciales, permisos y caducidad en cada solicitud protegida.
El cierre revoca el token en el almacenamiento. Aplicar el script
`base-datos/08-sesiones-administrativas.sql` antes de usar estas rutas:

| Metodo y ruta | Operacion |
|---|---|
| `POST /api/v1/acceso/administradores/sesiones` | Iniciar sesion con JSON `usuario` y `clave`. |
| `GET /api/v1/acceso/administradores/sesion` | Consultar la sesion autenticada. |
| `DELETE /api/v1/acceso/administradores/sesion` | Cerrar esa sesion. |
| `PUT /api/v1/acceso/administradores/sesion/clave` | Cambiar la propia clave y cerrar sus sesiones anteriores. |

La parte 3 incorpora `/api/v1/administracion/administradores`: listado,
consulta, alta, estado, permisos, restablecimiento de claves y auditoria.
Requiere `GESTIONAR_ADMINISTRADORES` y confirma la clave del actor para
mutaciones. Las cuentas nuevas deben cambiar su clave inicial; todos los
cambios efectivos revocan las sesiones de la cuenta afectada. Se conserva
al menos un gestor activo sin cambio de clave pendiente, incluso ante
modificaciones concurrentes. No requiere SQL adicional a los scripts 01-08.
La pantalla administrativa llegara con la interfaz.

La preparacion, los limites de entrada y los comandos estan en
[Cuentas administrativas](documentacion/cuentas-administrativas.md).
El contrato HTTP, los vencimientos y la prueba sin publicar el token estan en
[Sesiones administrativas](documentacion/sesiones-administrativas.md).
Las rutas, reglas y recorrido de otra persona estan en
[Gestion de administradores](documentacion/gestion-administradores.md).

## Configuracion y comprobaciones

La ejecucion normal utiliza `application.properties` y obtiene la
contrasena de `nivora_app` mediante la variable `NIVORA_DB_CLAVE`.
La contrasena no debe guardarse en los archivos del proyecto.

Desde `tecnologia/nivora-backend` en CMD:

```bat
mvnw.cmd clean verify
```

Las pruebas de arranque usan el perfil `test`, H2 en memoria y un
puerto HTTP temporal. Comprueban que la conexion de pruebas utiliza H2
y que `/actuator/health` responde con HTTP 200.

Las pruebas del adaptador levantan un servidor HTTP temporal en
`127.0.0.1` y comprueban el transporte real, respuestas validas, ausencia
confirmada, datos invalidos, errores HTTP, redirecciones, conexion
rechazada y tiempos de espera tanto en cabeceras como en el cuerpo.
No necesitan que MySQL ni la Empresa de Agua esten ejecutandose.

Las pruebas de `FuenteLecturasHttp` usan otro servidor HTTP temporal para
comprobar paginas validas y vacias, continuidad del cursor, ausencia confirmada,
identificadores grandes, datos incompatibles, limites de respuesta y errores
de red. Se conservan las pruebas del adaptador de suministros para comprobar
el transporte compartido y sus resultados.

Las pruebas de persistencia ejecutan `base-datos/02-suministros.sql` en H2
con modo MySQL y un esquema aislado. Comprueban insercion, actualizacion,
ceros iniciales, fechas UTC, restricciones, reintentos, escrituras retrasadas
y conservacion de datos ante fallos externos. Las pruebas de aplicacion
comprueban que solo se guarden suministros confirmados y que los errores
de fuente o persistencia no se conviertan en resultados satisfactorios.

Las pruebas de incorporacion ejecutan los scripts de suministros y lecturas
en H2 aislado. Comprueban identidad, fechas, medidores, contadores, conflictos,
conservacion de incidencias, dos escrituras simultaneas y rollback del lote.
Las pruebas de aplicacion comprueban la coordinacion entre fuente y repositorio.

Las pruebas de recuperacion incluyen el script `04-recuperacion-lecturas.sql`.
Comprueban reanudacion, recorrido del historial, fallos de fuente, incidencias,
bloqueo por datos rechazados, revisiones concurrentes y atomicidad del cursor.

Estas pruebas no comprueban la conexion a la Empresa de Agua instalada
en el equipo ni sustituyen la comprobacion manual con MySQL 8.0. Tampoco
comprueban las futuras reglas de negocio ni la capacidad de 5000 usuarios.

Con Nivora ejecutandose normalmente:

```bat
curl.exe -i "http://127.0.0.1:8082/actuator/health"
```

### Comprobacion manual con la Empresa de Agua local

Mantener la Empresa de Agua iniciada en el puerto 8081. Desde el backend,
con `NIVORA_DB_CLAVE` disponible en el entorno, ejecutar en CMD:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.empresa-agua.comprobar-al-iniciar=true --nivora.empresa-agua.codigo-prueba=13100390"
```

Esta ejecucion no abre el puerto 8082 y solo consulta el suministro.
Debe mostrar `Comprobacion Empresa de Agua: suministro 13100390 encontrado.`
Para comprobar la ausencia confirmada, usar `99999999` como codigo de
prueba. Si la fuente no esta disponible, la ejecucion falla con
`FuenteSuministrosNoDisponibleException`; no informa una ausencia falsa.

### Comprobacion manual del almacenamiento propio

Despues de crear la tabla, con `NIVORA_DB_CLAVE` disponible en el entorno,
ejecutar desde el backend en CMD. Para verificar y guardar un suministro,
mantener la Empresa de Agua encendida:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.suministros.comprobar-al-iniciar=true --nivora.suministros.operacion-prueba=actualizar --nivora.suministros.codigo-prueba=13100390"
```

Debe informar `suministro 13100390 verificado; registro propio disponible.`
La misma ejecucion puede repetirse sin generar otra fila para ese codigo.

Para consultar exclusivamente los datos guardados, usar:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.suministros.comprobar-al-iniciar=true --nivora.suministros.operacion-prueba=consultar --nivora.suministros.codigo-prueba=13100390"
```

Debe informar `suministro 13100390 encontrado en la base propia` y su fecha
de verificacion. Esta operacion permite comprobar la consulta con la Empresa
apagada y MySQL encendido. Para que la informacion sobreviva al proceso,
ambas ejecuciones normales utilizan MySQL, no el perfil de pruebas H2.

Estas comprobaciones no abren el puerto 8082. Estan desactivadas por defecto:

| Propiedad | Valor local |
|---|---|
| `nivora.suministros.comprobar-al-iniciar` | `false` |
| `nivora.suministros.operacion-prueba` | `consultar` |
| `nivora.suministros.codigo-prueba` | `13100390` |

### Comprobacion manual de las paginas de lecturas

Con la Empresa de Agua iniciada en el puerto 8081 y `NIVORA_DB_CLAVE`
disponible en el entorno, ejecutar desde el backend en CMD:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.lecturas.comprobar-al-iniciar=true"
```

La configuracion de la comprobacion es:

| Propiedad | Valor local |
|---|---|
| `nivora.lecturas.comprobar-al-iniciar` | `false` |
| `nivora.lecturas.codigo-prueba` | `13100390` |
| `nivora.lecturas.cursor-prueba` | `0` |
| `nivora.lecturas.limite-prueba` | `2` |

La ejecucion informa cantidad de lecturas, siguiente cursor y si hay mas.
Para consultar la siguiente pagina, enviar el cursor informado mediante
`--nivora.lecturas.cursor-prueba=...`. No se abre el puerto 8082 ni se
almacenan lecturas. La ejecucion habitual tiene la comprobacion desactivada.

### Preparar el almacenamiento de lecturas en MySQL

Ejecutar como administrador dentro del cliente `mysql>`:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/03-lecturas.sql;
SHOW TABLES FROM nivora;
```

La cuenta `nivora_app` conserva los permisos existentes sobre `nivora.*`.
El backend no ejecuta DDL durante el arranque.

### Comprobacion manual de incorporacion y consulta

Con el suministro registrado en Nivora, la Empresa de Agua encendida y
`NIVORA_DB_CLAVE` disponible en el entorno, ejecutar desde el backend en CMD:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.lecturas.almacenamiento.comprobar-al-iniciar=true --nivora.lecturas.almacenamiento.operacion-prueba=importar"
```

El codigo, cursor y limite se toman de las propiedades de prueba de lecturas
ya descritas. La primera pagina de dos lecturas informara nuevas 2, repetidas
0 y rechazadas 0 si todavia no fue incorporada. Repetirla debe informar nuevas
0 y repetidas 2. Una pagina con rechazos requiere revisar sus incidencias;
un `BUILD SUCCESS` solo acredita que termino la operacion, no que todas las
lecturas se aceptaron.

Para consultar exclusivamente la base propia:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.lecturas.almacenamiento.comprobar-al-iniciar=true --nivora.lecturas.almacenamiento.operacion-prueba=consultar"
```

Esta consulta informa el total de lecturas aceptadas e incidencias del
suministro. Funciona con la Empresa apagada y MySQL encendido. Las dos
operaciones son comprobaciones por consola y no abren el puerto 8082.

| Propiedad | Valor local |
|---|---|
| `nivora.lecturas.almacenamiento.comprobar-al-iniciar` | `false` |
| `nivora.lecturas.almacenamiento.operacion-prueba` | `consultar` |

El comando `importar` de esta seccion no guarda ni confirma el cursor. El
siguiente cursor mostrado es informacion de la pagina recibida. El recuperador
descrito en su documentacion utiliza un progreso persistido y separado.

## Referencias de arquitectura

- [Modulos del backend](../../docs/decisiones/ADR-001-monolito-modular.md).
- [Clean Architecture](../../docs/decisiones/ADR-002-clean-architecture.md).
- [Actualizacion y continuidad](../../docs/decisiones/ADR-003-actualizacion-y-continuidad.md).
- [Contratos y adaptadores](../../docs/decisiones/ADR-004-integraciones-externas.md).
