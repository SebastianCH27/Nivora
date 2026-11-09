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
de consumo continuo.

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
por las cargas inicial y diaria, sin necesitar tablas de consumo mensual.

### Índice para una base ya instalada

Con la Empresa de Agua detenida, ejecutar como administrador de MySQL:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/empresa-agua/base-datos/07-indice-lecturas.sql;
SHOW INDEX FROM empresa_agua.lecturas WHERE Key_name = 'idx_lecturas_suministro_id';
```

El índice combina suministro e ID de lectura. El script conserva los datos
y puede repetirse: comprueba si el índice ya existe antes de crearlo.

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
- `hayMas` indica si quedan filas en esa consulta; no acredita que el
  generador esté al día ni impide que aparezcan nuevas filas más adelante.
- Una página vacía conserva el cursor recibido y devuelve HTTP 200 si
  el suministro existe. Un suministro inexistente devuelve HTTP 404.
- Repetir el cursor permite volver a consultar las mismas filas. Nivora
  deberá reconocerlas por fuente e ID, validar su contenido y evitar
  incorporarlas dos veces.

Esta consulta no es un registro de modificaciones: editar una fila existente
no le asigna otro ID. La coordinación de productores, la confirmación de
lotes y la recuperación de cambios concurrentes se definirán antes de
utilizarla como mecanismo de sincronización continua.

### Continuidad de la generación: pendiente

Actualmente `GeneradorConsumoContinuo` actualiza `consumos_mensuales`,
pero no inserta nuevas filas en `lecturas`. La ruta expone únicamente
las lecturas que ya existen en esa tabla. Falta conectar la generación
continua con la entrega de mediciones identificadas y comprobar que
coincidan con los consumos e históricos. Ese trabajo no se sustituye
convirtiendo cada consulta del acumulado mensual en un incremento nuevo.

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
