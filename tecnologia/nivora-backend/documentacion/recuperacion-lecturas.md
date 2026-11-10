# Recuperacion manual de lecturas

Este avance recorre paginas historicas de la Empresa de Agua utilizando
el almacenamiento y la deduplicacion de Nivora. Cada ejecucion procesa
una cantidad limitada de paginas y conserva su avance por fuente y suministro.
El suministro debe estar registrado previamente en `nivora.suministros`.

## Flujo y confirmacion

1. Consultar el progreso propio del suministro.
2. Solicitar a la fuente una pagina desde ese cursor, fuera de una transaccion de escritura.
3. Comprobar que la revision del progreso siga vigente.
4. Incorporar lecturas e incidencias y confirmar el avance en una sola transaccion.
5. Continuar hasta completar el recorrido, encontrar un bloqueo o alcanzar el limite de paginas.

El progreso no se infiere de la lectura con mayor ID que ya exista en Nivora.
Una importacion manual previa puede haber dejado huecos: el primer recorrido
comienza en el cursor `0` y vuelve a comprobar los registros existentes.

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
El reinicio conserva tanto las lecturas como las incidencias anteriores.

Los fallos de fuente se registran por tipo en una tabla de incidencias de
recuperacion. Repetir un fallo incrementa su contador, sin duplicar la fila.
Una pagina recibida correctamente cierra las incidencias de fuente abiertas
para ese suministro, conservando sus fechas y contadores historicos.
Los rechazos de lecturas se revisan en `incidencias_lecturas`, por separado.

## Alcance del recorrido

La recuperacion actual de Nivora consulta filas persistidas sin enviar un
limite superior de IDs. Por eso cada nuevo recorrido, una vez
terminado el anterior, vuelve a revisar el historial completo. Asi puede
recuperar posteriormente una fila con ID inferior al ultimo cursor de un
recorrido anterior y detectar cambios de una lectura ya aceptada.

`RECORRIDO_COMPLETO` no certifica que el proveedor este actualizado hasta
la hora actual ni representa una fotografia inmutable de todas las paginas.
La Empresa ya publica nuevas lecturas identificadas junto con sus totales
mensuales y la recuperacion manual puede incorporarlas. La sincronizacion
automatica de Nivora y su frecuencia siguen pendientes. La marca de emision
de la Empresa por si sola no fija el limite de las paginas de Nivora.

El proveedor ofrece ahora `GET /api/v1/lecturas/corte`, el parametro opcional
`hastaId` y `GET /api/v1/suministros/{codigo}/medidores`. El siguiente avance
de Nivora debe consumir ese contexto y persistir el corte de cada recorrido.
Hasta entonces, este recuperador conserva su comportamiento anterior. El
nuevo contrato del proveedor acota altas; no versiona cambios historicos.

Las metas de rendimiento y 5000 usuarios simultaneos requieren sus pruebas
posteriores. La revision completa del historial y el bloqueo por fuente
deberan evaluarse junto con esas pruebas antes de fijar frecuencias automaticas.

## Preparar MySQL

Despues de `03-lecturas.sql`, ejecutar como administrador en `mysql>`:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/04-recuperacion-lecturas.sql;
SHOW TABLES FROM nivora;
```

El script crea `progreso_recuperacion_lecturas` e
`incidencias_recuperacion_lecturas`. Conserva las tablas y los datos previos.
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
`BLOQUEADO` o `SUMINISTRO_NO_ENCONTRADO` requieren revisar el estado aunque
el comando termine con `BUILD SUCCESS`. Un fallo de fuente se registra y
propaga como excepcion; en ese caso se espera `BUILD FAILURE`.

Para consultar solamente el progreso y el numero de lecturas propias:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.recuperacion.comprobar-al-iniciar=true --nivora.recuperacion.operacion-prueba=consultar"
```

La consulta del estado funciona con MySQL encendido y la Empresa apagada.

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
Este avance no inicia tareas programadas ni implementa el acceso con DNI.
