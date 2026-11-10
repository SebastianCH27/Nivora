# Empresa de Agua

Servicio externo que proporciona a Nivora datos de consumo de agua,
historial mensual, tarifas y recibos mediante una API REST.

Implementado con Java 21, Spring Boot 4.1.2 y MySQL 8.0.

Los titulares, suministros, consumos y precios son ficticios. Permiten
desarrollar Nivora mientras no exista la integración con los medidores
inteligentes de una empresa real.

## Funcionamiento

- La carga inicial prepara 100 000 suministros con códigos del
  13100000 al 13199999.
- El consumo mensual aumenta durante el día con perfiles distintos
  para cada vivienda, incluyendo actividad y pausas.
- Las consultas leen los datos almacenados; no generan consumo.
- Al comenzar un mes en America/Lima, el periodo anterior se cierra
  y el nuevo empieza en cero litros. El historial se conserva.
- La emisión automática genera los recibos de periodos cerrados
  que todavía no tienen uno.
- Al reiniciar el servicio, el generador procesa el tiempo pendiente
  desde el último avance confirmado.
- El generador publica lecturas acumuladas identificadas con un intervalo
  configurable de cinco minutos. Omite contadores sin cambios y conserva
  una lectura de referencia al cerrar cada mes.

Este servicio representa al proveedor de datos. El backend propio
de Nivora se implementará por separado y consumirá esta API.

## Requisitos

- JDK 21.
- MySQL Server 8.0 activo en 127.0.0.1:3306.
- Cuenta `empresa_app`@`127.0.0.1` con permisos SELECT, INSERT y UPDATE
  sobre `empresa_agua.*`.
- Maven Wrapper incluido en el proyecto. La primera ejecución
  necesita descargar sus dependencias.

## Iniciar con la base ya preparada

Desde la carpeta `tecnologia\empresa-agua`, ejecutar en CMD:

```bat
set /p "EMPRESA_DB_CLAVE=Escribe la contraseña de empresa_app: "
mvnw.cmd spring-boot:run
```

Introducir la contraseña cuando CMD la solicite. La variable se establece
en esa ventana; una ventana nueva necesita configurarla otra vez.

La API se ejecuta en `http://127.0.0.1:8081`.

Para detenerla, presionar Ctrl + C en la ventana de Spring Boot.
Los datos confirmados permanecen en MySQL.

## Preparar una instalación nueva

Esta sección se ejecuta sobre una base vacía, con el servicio detenido.

Desde la carpeta `tecnologia\empresa-agua`, abrir el cliente MySQL
como administrador de la base y ejecutar los scripts en este orden:

```sql
SOURCE base-datos/01-estructura.sql;
SOURCE base-datos/02-tarifas-recibos.sql;
SOURCE base-datos/03-control-generacion.sql;
SOURCE base-datos/04-consumos-mensuales.sql;
SOURCE base-datos/05-control-consumo-continuo.sql;
SOURCE base-datos/06-detalle-recibos.sql;
SOURCE base-datos/07-indice-lecturas.sql;
SOURCE base-datos/08-control-emision-lecturas.sql;
```

La cuenta `empresa_app` debe estar creada con los permisos indicados.

Al terminar, salir de MySQL y configurar `EMPRESA_DB_CLAVE` en CMD.
Ejecutar por separado y en orden:

```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --empresa.carga-inicial.habilitada=true"

mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --empresa.lecturas.habilitada=true"

mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --empresa.carga-mensual.habilitada=true"

mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --empresa.carga-tarifas.habilitada=true"

mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --empresa.carga-recibos.habilitada=true"
```

Continuar con el siguiente comando cuando el anterior termine
correctamente. Estos procesos preparan los datos históricos,
las asignaciones de tarifa y los recibos iniciales.

La generación diaria se conserva como herramienta de preparación
del historial inicial. El funcionamiento normal utiliza el generador
de consumo continuo. Una vez confirmada la primera emisión continua,
la herramienta diaria informa que la preparación inicial ya terminó.

Al completar las cargas, iniciar normalmente:

```bat
mvnw.cmd spring-boot:run
```

Las opciones de carga inicial permanecen desactivadas
en `application.properties`.

## API disponible

Todas las rutas de consulta utilizan GET. El código de suministro
se envía como texto de 1 a 20 dígitos.

| Ruta | Resultado |
|---|---|
| `/actuator/health` | Estado del servicio y conexión de datos |
| `/api/v1/suministros/{codigo}` | Código y titular del suministro, sin depender de sus consumos mensuales |
| `/api/v1/suministros/{codigo}/lecturas?despuesDe=0&limite=100` | Página de lecturas identificadas ya persistidas, ordenadas por ID |
| `/api/v1/lecturas/corte` | Límite global de altas confirmadas y fecha de la emisión que lo confirma |
| `/api/v1/suministros/{codigo}/medidores` | Inventario de medidores del suministro con sus fechas administrativas |
| `/api/v1/suministros/{codigo}/consumo-actual` | Consumo acumulado del mes en litros |
| `/api/v1/suministros/{codigo}/historial?meses=12` | Historial mensual, del más reciente al más antiguo |
| `/api/v1/suministros/{codigo}/recibos?limite=12` | Recibos emitidos y disponibles para consultar |
| `/api/v1/suministros/{codigo}/tarifa-actual` | Tarifa asignada y vigente en la fecha de consulta |

`meses` y `limite` aceptan valores entre 1 y 60.
Su valor predeterminado es 12.

Las respuestas de suministros, lecturas, consumo, historial, recibos y tarifas
incluyen `Cache-Control: no-store`.

- 400: código o parámetro inválido.
- 404: suministro inexistente.
- 503: datos necesarios no disponibles, configuración incompleta
  o fallo de acceso a la base de datos.

## Lecturas identificadas para Nivora

La nueva ruta consulta la tabla `lecturas`. No genera mediciones ni modifica
los totales mensuales. Permite recuperar las lecturas históricas preparadas
por las cargas inicial y diaria, además de las nuevas mediciones publicadas
por el generador continuo. La consulta de filas ya guardadas no necesita
las tablas de consumo mensual.

### Índice para una base ya instalada

Con la Empresa de Agua detenida, ejecutar como administrador de MySQL:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/empresa-agua/base-datos/07-indice-lecturas.sql;
SHOW INDEX FROM empresa_agua.lecturas WHERE Key_name = 'idx_lecturas_suministro_id';
```

El índice combina suministro e ID de lectura. El script conserva los datos
y puede repetirse: comprueba si el índice ya existe antes de crearlo.

Antes de iniciar la emisión continua en una base instalada, ejecutar también
con el servicio detenido:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/empresa-agua/base-datos/08-control-emision-lecturas.sql;
SELECT id_control, ultima_emision_utc FROM empresa_agua.control_emision_lecturas;
```

El script prepara una fila de control. La primera vez su fecha es `NULL`.
Repetirlo conserva una fecha que ya haya sido confirmada.

### Contrato de la página

```json
{
  "fuente": "empresa-agua",
  "codigoSuministro": "13100390",
  "tipoMedicion": "ACUMULADA",
  "unidad": "L",
  "lecturas": [
    {
      "idLectura": "100391",
      "idMedidor": "391",
      "fechaMedicion": "2026-10-01T05:00:00Z",
      "valorLitros": 123456
    }
  ],
  "siguienteDespuesDe": "100391",
  "hayMas": true
}
```

Los valores del ejemplo son ilustrativos. Los ID corresponden a las filas
persistidas y se transmiten como texto para conservar todos sus dígitos.
La fecha representa la medición en UTC. El valor es el contador acumulado
del medidor convertido de m³ a litros enteros, no el incremento de consumo
ni el total de un mes. Los acumulados de medidores distintos no se restan
entre sí.

- `despuesDe` es un ID exclusivo, no una fecha. Acepta de 0 a 9223372036854775807.
- `limite` acepta de 1 a 500; por defecto devuelve hasta 100 lecturas.
- Para continuar, enviar `siguienteDespuesDe` como el siguiente `despuesDe`.
- `hastaId` es opcional y limita las filas a IDs menores o iguales a ese valor.
  Debe ser un decimal canónico entre 0 y 9223372036854775807, mayor o igual
  a `despuesDe`. Un valor vacío o inválido devuelve HTTP 400.
- `hayMas` indica si quedan filas dentro del límite solicitado; no acredita que el
  generador esté al día ni impide que aparezcan nuevas filas más adelante.
- Una página vacía conserva el cursor recibido y devuelve HTTP 200 si
  el suministro existe. Un suministro inexistente devuelve HTTP 404.
- Repetir el cursor permite volver a consultar las mismas filas. Nivora
  deberá reconocerlas por fuente e ID, validar su contenido y evitar
  incorporarlas dos veces.

Esta consulta no es un registro de modificaciones: editar una fila existente
no le asigna otro ID. Los generadores diario y continuo se coordinan mediante
la fila de control de emisión. Las altas de estos generadores conservan las
lecturas anteriores. La corrección o eliminación manual de filas queda fuera
de la garantía de altas confirmadas del contrato siguiente.

### Corte confirmado e inventario

`GET /api/v1/lecturas/corte` devuelve:

```json
{
  "fuente": "empresa-agua",
  "hastaIdLectura": "4000000",
  "confirmadoHasta": "2026-11-10T17:49:11Z"
}
```

Los valores son ilustrativos. El ID es un límite global de la fuente,
transmitido como texto; puede pertenecer a otro suministro. La fecha está
en UTC y corresponde a la emisión confirmada, incluso si un hogar no
cambió su contador. No equivale a su última medición ni a la hora actual.

La captura usa una transacción READ COMMITTED y bloquea brevemente la misma
fila de control que el productor. Obtiene el máximo ID después de adquirir
ese bloqueo y no mezcla una fecha anterior con un máximo de otra publicación.
Su transacción tiene un límite de diez segundos; una espera o fallo de datos
se informa con HTTP 503. Antes de la primera emisión confirmada también
devuelve 503. La consulta no ejecuta el generador.

Para recuperar un conjunto acotado de altas:

1. Consultar el corte una vez al comenzar el recorrido y conservar ambos campos.
2. Enviar su `hastaIdLectura` como `hastaId` en todas las páginas.
3. Continuar con `siguienteDespuesDe` hasta que `hayMas` sea `false`.
4. Obtener un nuevo corte para incluir las publicaciones posteriores.

Las altas posteriores de los productores reciben IDs superiores y quedan
fuera del recorrido anterior. `hastaId` por sí solo es un filtro: el cliente
debe conservar el corte que obtuvo, sin cambiarlo entre páginas. El contrato
no versiona modificaciones ni borrados históricos. Sin `hastaId`, la ruta
mantiene su comportamiento anterior.

`GET /api/v1/suministros/{codigo}/medidores` devuelve `fuente`,
`codigoSuministro`, `zonaFechas: "America/Lima"` y la lista `medidores`.
Cada elemento contiene `idMedidor` como texto, `fechaInstalacion` y
`fechaRetiro`, que es `null` mientras no exista retiro registrado. Incluye
medidores anteriores y actuales, ordenados por instalación e ID, únicamente
del suministro solicitado. Las fechas administrativas tienen precisión de
día y no describen la hora exacta de una sustitución.

Un suministro inexistente devuelve 404. Un inventario vacío, inválido o
superior a 1000 elementos devuelve 503, sin truncarlo silenciosamente.
El inventario no tiene versión histórica asociada al corte. La gestión
completa de sustituciones y correcciones de medidores sigue pendiente.

La recuperación manual principal de Nivora consume estas rutas y conserva
el corte y una copia del inventario durante cada recorrido. La recuperación
automática sigue pendiente.

### Emisión continua de lecturas

`GeneradorConsumoContinuo` actualiza los totales mensuales y llama a
`PublicadorLecturasContinuas` dentro de la misma transacción de cada avance.
Las lecturas nuevas, el total mensual y sus controles se confirman juntos.
Un fallo revierte ese avance; los avances anteriores confirmados se conservan.

El publicador obtiene primero hasta 1000 suministros y consulta sus datos
y sus últimas mediciones en consultas separadas por lote. Comprueba un único
medidor activo por suministro, su referencia de inicio del mes y que el
contador no retroceda ni contradiga una medición ya guardada.

| Propiedad | Valor inicial | Uso |
|---|---|---|
| `empresa.lecturas-continuas.intervalo-segundos` | `300` | Intervalo mínimo entre ciclos ordinarios de publicación. |
| `empresa.lecturas-continuas.consulta-maxima-segundos` | `30` | Límite configurado para cada consulta de lectura del publicador. |

El cierre mensual publica una referencia aunque no haya transcurrido el
intervalo o el contador no cambie. El contador del medidor continúa en el
nuevo mes, mientras el consumo mensual empieza en cero. Las mediciones
conservan resolución de un litro; los totales mensuales mantienen sus
decimales y pueden superar en menos de un litro la diferencia de contadores.

La simulación requiere una referencia del medidor al inicio del mes.
La gestión completa de sustituciones de medidores queda pendiente.

Los mensajes de avance muestran filas preparadas dentro de la transacción.
La marca `control_emision_lecturas.ultima_emision_utc` representa el último
ciclo de publicación confirmado, incluso cuando algunos hogares no cambian
su contador. No equivale a la última medición de cada hogar ni a un corte
inmutable de páginas para Nivora. `/actuator/health` comprueba el servicio y
su conexión de datos; la marca y el registro permiten comprobar el generador.

Nivora puede recuperar estas lecturas mediante su proceso manual existente.
La programación automática de esa recuperación sigue pendiente.

## Unidades, tarifas y fechas

- El consumo mensual se expresa en litros.
- 1000 litros equivalen a 1 m³.
- Los precios se expresan en soles por m³ y se aplican por porciones.
- El cargo fijo inicial es S/ 5.00.
- Los primeros 10 m³ cuestan S/ 1.50 por m³.
- Los siguientes 10 m³ cuestan S/ 2.20 por m³.
- El consumo que supera los 20 m³ cuesta S/ 3.00 por m³.

Estos precios son supuestos del proyecto. Una sola tarifa debe cubrir
todo el mes que se factura.

Los recibos conservan el consumo y los cargos calculados al emitirse.
Su disponibilidad es el primer día del mes siguiente y su vencimiento,
siete días después.

Los instantes se almacenan en UTC. En la API, el sufijo `Z` identifica
esa zona. Los meses y fechas comerciales se interpretan en America/Lima.

`actualizadoHasta` indica hasta qué instante se procesó el generador.
`fechaActualizacion` del historial indica la actualización del registro
mensual. Pueden diferir cuando una vivienda no está consumiendo.

La configuración espera dos segundos entre ciclos de consumo y sesenta
entre revisiones de recibos, después de terminar cada ejecución.
El tiempo de procesamiento también influye en la actualización.

## Verificación

La prueba de arranque utiliza el perfil `test` y una base H2 en memoria.
Las cargas iniciales y tareas automáticas están desactivadas
en ese perfil.

La consulta independiente del suministro añade seis pruebas HTTP con
una base H2 exclusiva: suministro existente sin tablas de consumo,
ceros iniciales, suministro inexistente, código con letras, longitud
inválida y fallo de acceso a datos. Incluida la prueba de arranque,
esas comprobaciones no dependen de los registros del MySQL de desarrollo.

`LecturasApiTests` añade pruebas HTTP sobre una base H2 separada para
validar el contrato de lecturas, paginación, reintentos, aislamiento entre
suministros, conversión exacta a litros, fechas UTC, identificadores grandes,
parámetros inválidos, páginas vacías y errores de datos. Las pruebas no
comprueban el índice en MySQL ni la futura sincronización de Nivora.

`LecturasContinuasTests` añade 14 pruebas de generación, intervalos,
repeticiones, fracciones, cierre mensual, reinicios, validaciones de
medidores, rollback entre lotes y selección de la última fecha aunque un
registro antiguo tenga mayor ID.

`CorteLecturasApiTests` añade 25 pruebas HTTP del corte, IDs grandes,
paginación acotada mientras aparecen nuevas filas, compatibilidad de la
consulta anterior, parámetros, inventario, aislamiento y errores. La suite
completa contiene 68 pruebas. Se comprueba además la captura coordinada
con una publicación sin confirmar usando MySQL 8.0.46.

La revisión de este avance incluyó una comprobación local con MySQL 8.0.46,
100 000 suministros y 3 700 000 lecturas iniciales: publicación, coherencia
de los contadores, repetición, intervalo y reinicio. Esta comprobación de
integración no acredita el objetivo posterior de 5000 usuarios simultáneos.

```bat
mvnw.cmd clean verify
```

Con el servicio activo, comprobar la integración con MySQL:

```bat
curl.exe -i "http://127.0.0.1:8081/actuator/health"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390/lecturas?despuesDe=0&limite=2"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390/consumo-actual"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390/historial?meses=12"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390/recibos?limite=1"

curl.exe -i "http://127.0.0.1:8081/api/v1/suministros/13100390/tarifa-actual"
```

### Resultados verificados el 6 de noviembre de 2026

- Compilación y empaquetado: BUILD SUCCESS.
- Prueba de arranque: una prueba, sin fallos ni errores.
- Octubre: 100 000 consumos mensuales cerrados.
- Noviembre: 100 000 consumos mensuales en curso.
- Octubre: 100 000 recibos con detalle, importes y consumos
  coincidentes con el periodo cerrado.
- Recibos pendientes para periodos cerrados: 0.

Las pruebas de carga para evaluar el objetivo de 5000 usuarios
concurrentes quedan pendientes.
