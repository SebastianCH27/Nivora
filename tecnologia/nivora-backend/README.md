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
| `acceso` | Acceso y suministros | Consulta HTTP, actualizacion explicita y repositorio propio de suministros. |
| `lecturas` | Lecturas, consumo e historial | Modelos y adaptador HTTP de paginas; incorporacion y calculos pendientes. |
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
un nuevo acceso. La validacion del DNI, la sesion y el registro del acceso
se implementaran en sus siguientes casos de uso. El titular del suministro
y la persona que consulta pueden ser diferentes.

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

Este avance incorpora la consulta externa y el almacenamiento de suministros.
Aun faltan el caso de uso de acceso, validacion de identidad, sesiones,
roles, y el almacenamiento y sincronizacion de lecturas, tarifas y recibos.
Las comprobaciones de consola no publican endpoints de consulta en Nivora.

La Empresa de Agua incorpora `GET /api/v1/suministros/{codigo}/lecturas`,
con paginas de mediciones historicas identificadas, medidor, fecha UTC,
tipo acumulado y unidad en litros. Los detalles del cursor estan en su README.
Nivora implementa su adaptador y las validaciones de estructura y paginacion.
El almacenamiento, deduplicacion y confirmacion de esas paginas siguen pendientes.

El generador continuo de la Empresa actualiza actualmente los totales
mensuales, no la tabla de lecturas historicas. Su entrega continua y
la recuperacion de pendientes deben completarse segun ADR-003.
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
con errores se rechaza completa. No se corrige silenciosamente ni se avanza
un cursor persistido desde este adaptador.

`ClienteEmpresaAgua`, en infraestructura compartida, concentra la conexion,
los tiempos de espera, la lectura JSON y el rechazo de redirecciones.
Los adaptadores mantienen separados sus resultados y excepciones de negocio.
El limite del cuerpo es de 64 KiB para suministros y 512 KiB para lecturas.

Las validaciones temporales respecto del corte de la fuente, la consistencia
entre mediciones, el control de cambios de medidor, las incidencias y la
deduplicacion contra datos ya almacenados pertenecen a la futura incorporacion.
Este avance no calcula consumo ni trata cada contador como un incremento nuevo.

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

## Referencias de arquitectura

- [Modulos del backend](../../docs/decisiones/ADR-001-monolito-modular.md).
- [Clean Architecture](../../docs/decisiones/ADR-002-clean-architecture.md).
- [Actualizacion y continuidad](../../docs/decisiones/ADR-003-actualizacion-y-continuidad.md).
- [Contratos y adaptadores](../../docs/decisiones/ADR-004-integraciones-externas.md).
