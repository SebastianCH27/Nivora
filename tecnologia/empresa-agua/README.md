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
| `/api/v1/suministros/{codigo}/consumo-actual` | Consumo acumulado del mes en litros |
| `/api/v1/suministros/{codigo}/historial?meses=12` | Historial mensual, del más reciente al más antiguo |
| `/api/v1/suministros/{codigo}/recibos?limite=12` | Recibos emitidos y disponibles para consultar |
| `/api/v1/suministros/{codigo}/tarifa-actual` | Tarifa asignada y vigente en la fecha de consulta |

`meses` y `limite` aceptan valores entre 1 y 60.
Su valor predeterminado es 12.

Las respuestas de consumo, historial, recibos y tarifas
incluyen `Cache-Control: no-store`.

- 400: código o parámetro inválido.
- 404: suministro inexistente.
- 503: datos necesarios no disponibles, configuración incompleta
  o fallo de acceso a la base de datos.

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

```bat
mvnw.cmd clean verify
```

Con el servicio activo, comprobar la integración con MySQL:

```bat
curl.exe -i "http://127.0.0.1:8081/actuator/health"

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