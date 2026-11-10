# Recuperacion manual de lecturas

Este avance recorre paginas historicas de la Empresa de Agua utilizando
el almacenamiento y la deduplicacion de Nivora. Cada ejecucion procesa
una cantidad limitada de paginas y conserva su avance por fuente y suministro.
El suministro debe estar registrado previamente en `nivora.suministros`.

## Flujo y confirmacion

1. Consultar el progreso propio del suministro.
2. Si no tiene contexto, obtener el inventario y el corte de la fuente fuera de las escrituras.
3. Guardar el contexto y una nueva revision del progreso atomicamente, comenzando desde cero.
4. Solicitar paginas desde el cursor confirmado, siempre con el mismo `hastaId` guardado.
5. Comprobar que la revision siga vigente y validar la fecha y el medidor de cada lectura.
6. Incorporar lecturas e incidencias y confirmar el avance en una sola transaccion.
7. Continuar hasta completar el corte, encontrar un bloqueo o alcanzar el limite de paginas.

El progreso no se infiere de la lectura con mayor ID que ya exista en Nivora.
Una importacion manual previa puede haber dejado huecos: el primer recorrido
comienza en el cursor `0` y vuelve a comprobar los registros existentes.
Esto tambien ocurre al adaptar un progreso anterior que no tenga contexto.
Los recorridos con contexto ya guardado reanudan el mismo corte e inventario,
sin volver a pedirlos aunque la fuente haya publicado nuevas mediciones.

Si otra ejecucion modifico el progreso durante la llamada HTTP, la pagina
obsoleta no se confirma. Se informa `ProgresoRecuperacionModificadoException`
y debe consultarse de nuevo el estado antes de reintentar.

La fecha de ultima confirmacion representa el procesamiento de una pagina
en Nivora. Es distinta de la fecha de medicion y del corte de datos
confirmado por la fuente.

## Estados

| Estado | Significado y siguiente accion |
|---|---|
| `PENDIENTE` | Recorrido nuevo, desde el cursor cero. |
| `EN_CURSO` | Hay paginas pendientes; la proxima ejecucion continua desde el cursor guardado. |
| `RECORRIDO_COMPLETO` | La ultima pagina indico que no habia mas filas en esa consulta. La siguiente ejecucion inicia otro recorrido desde cero. |
| `ERROR_FUENTE` | La consulta fallo o devolvio datos incompatibles. Se conserva el cursor para el siguiente intento. |
| `SUMINISTRO_NO_ENCONTRADO` | La fuente confirmo ausencia. Se conserva la informacion propia y el cursor; puede consultarse de nuevo posteriormente. |
| `BLOQUEADO` | La pagina contenia lecturas rechazadas. Debe revisarse la incidencia antes de reiniciar el recorrido. |

Una pagina con lecturas rechazadas conserva las otras lecturas aceptadas
y sus incidencias, pero no adelanta el cursor. Una nueva llamada a recuperar
un recorrido bloqueado informa su estado sin consultar otra pagina.
El reinicio conserva tanto las lecturas como las incidencias anteriores y
descarta el contexto del recorrido, para obtener uno nuevo en la proxima ejecucion.

Los fallos de fuente se registran por tipo en una tabla de incidencias de
recuperacion. Repetir un fallo incrementa su contador, sin duplicar la fila.
Una pagina recibida correctamente cierra las incidencias de fuente abiertas
para ese suministro, conservando sus fechas y contadores historicos.
Los rechazos de lecturas se revisan en `incidencias_lecturas`, por separado.

## Alcance del recorrido

La recuperacion principal envia el limite superior de IDs de su contexto.
Cada nuevo recorrido, una vez terminado el anterior, obtiene otro corte y
vuelve a revisar el historial completo. Asi puede
recuperar posteriormente una fila con ID inferior al ultimo cursor de un
recorrido anterior y detectar cambios de una lectura ya aceptada.

`RECORRIDO_COMPLETO` confirma el procesamiento sin rechazos de las paginas
del corte de ese recorrido. No certifica que el proveedor este actualizado
hasta la hora actual ni versiona modificaciones o borrados historicos.
La Empresa ya publica nuevas lecturas identificadas junto con sus totales
mensuales. La recuperacion manual y la tarea automatica pueden incorporarlas.
Su programacion se describe en [Sincronizacion](sincronizacion-lecturas.md). La marca de emision
de la Empresa se conserva en el contexto junto con el limite de sus paginas.

El adaptador consulta `GET /api/v1/suministros/{codigo}/medidores` y
`GET /api/v1/lecturas/corte`. Exige fuente y suministro compatibles, fechas
validas, UTC para el corte y `America/Lima` para los dias administrativos.
Rechaza cortes futuros o incompatibles, inventarios vacios o mayores de 1000
medidores y cuerpos que excedan los limites HTTP. No recurre a una consulta
sin corte si esos datos no estan disponibles.

Los IDs de pagina deben avanzar y no superar el limite. Un incumplimiento
del protocolo se registra como `ERROR_FUENTE`, conservando cursor y contexto.
Las fechas posteriores al corte, medidores desconocidos y fechas fuera de
la vigencia administrativa se registran como incidencias de lectura y
bloquean el avance. Las lecturas identicas ya almacenadas tambien se revisan
contra el contexto antes de contarlas como repetidas; se conservan si surge
una incompatibilidad, junto con su incidencia.

Los dias de instalacion y retiro se consideran incluidos y se comparan en
America/Lima. Esta comprobacion no determina la hora fisica de sustitucion.
El inventario no esta versionado por la fuente: se conserva la copia obtenida
al iniciar el recorrido. Gestionar sustituciones con precision horaria sigue
pendiente.

Las metas de rendimiento y 5000 usuarios simultaneos requieren sus pruebas
posteriores. La revision completa del historial y el bloqueo por fuente
deberan evaluarse junto con esas pruebas para ajustar los limites y frecuencias
de la programacion inicial.

## Preparar MySQL

Despues de `03-lecturas.sql`, ejecutar como administrador en `mysql>`:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/04-recuperacion-lecturas.sql;
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/05-contexto-recuperacion-lecturas.sql;
SHOW TABLES FROM nivora;
```

El script crea `progreso_recuperacion_lecturas` e
`incidencias_recuperacion_lecturas`. Conserva las tablas y los datos previos.
El script 05 agrega `contexto_recuperacion_lecturas`, con el limite del corte,
la fecha confirmada por la fuente, su inventario JSON y la fecha de captura
local. Repetirlo conserva el contexto existente. Se guarda el contexto del
recorrido actual o ultimo completado; no es un historial de inventarios.
La cuenta de ejecucion mantiene los permisos existentes sobre `nivora.*`.
El backend no aplica DDL automaticamente.

## Comprobaciones por consola

Desde `tecnologia/nivora-backend`, con `NIVORA_DB_CLAVE` disponible en el
entorno y la Empresa de Agua encendida, ejecutar en CMD:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.recuperacion.comprobar-al-iniciar=true"
```

El resultado informa las paginas procesadas en esa ejecucion, lecturas
nuevas, repetidas, rechazadas, cursor guardado y estado del recorrido.
Una segunda linea informa el corte objetivo, la fecha confirmada por la
fuente y el numero de medidores. En `EN_CURSO` aun faltan paginas de ese corte.
`BLOQUEADO` o `SUMINISTRO_NO_ENCONTRADO` requieren revisar el estado aunque
el comando termine con `BUILD SUCCESS`. Un fallo de fuente se registra y
propaga como excepcion; en ese caso se espera `BUILD FAILURE`.

Para consultar solamente el progreso y el numero de lecturas propias:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.recuperacion.comprobar-al-iniciar=true --nivora.recuperacion.operacion-prueba=consultar"
```

La consulta del estado y del contexto funciona con MySQL encendido y la
Empresa apagada. Un contexto guardado con datos corruptos informa un fallo
de almacenamiento; no se reemplaza silenciosamente ni consulta otra fuente.

Para reiniciar explicitamente el recorrido despues de revisar su causa:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.recuperacion.comprobar-al-iniciar=true --nivora.recuperacion.operacion-prueba=reiniciar"
```

El reinicio devuelve el cursor a cero, incrementa la revision y conserva
las lecturas e incidencias. No confirma que la causa del rechazo haya sido corregida.

| Propiedad | Valor local |
|---|---|
| `nivora.recuperacion.comprobar-al-iniciar` | `false` |
| `nivora.recuperacion.operacion-prueba` | `recuperar` |
| `nivora.recuperacion.maximo-paginas` | `2` |
| `nivora.lecturas.codigo-prueba` | `13100390` |
| `nivora.lecturas.limite-prueba` | `2` |

El limite de pagina acepta 1-500 lecturas y el maximo por ejecucion 1-100
paginas. El recuperador toma su cursor de la tabla de progreso. La propiedad
`nivora.lecturas.cursor-prueba` pertenece a las comprobaciones de una pagina
individual, que siguen disponibles.

La comprobacion esta desactivada por defecto y no abre el puerto 8082.
Las comprobaciones no web no inician la tarea programada del servidor web.
El acceso con DNI sigue pendiente.

## Verificacion

Las 165 pruebas anteriores se conservan. `RecuperacionConCorteTests` agrega
35 pruebas con HTTP local y H2 sobre contexto persistido, reanudacion,
publicaciones posteriores, rechazos, fallos de fuente, cambios concurrentes
de recorrido, migracion repetida y rollback. La suite completa contiene 200
pruebas de recuperacion. Las 31 pruebas adicionales de sincronizacion llevan
la suite general a 231. La integracion tambien se comprobo con la API Spring real de la
Empresa y MySQL 8.0.46, conservando datos de un progreso anterior y validando
la ruta del comprobador principal.
