# Verificacion de la documentacion de la API

Bloque 9, parte 4. La guia [api.md](api.md) y el contrato
[OpenAPI 3.1](openapi.json) describen el backend revisado sobre `4fd156a`.

## Comprobaciones incorporadas

`DocumentacionApiTests` inicia el contexto Spring con el perfil `test`.
Utiliza los mappings registrados por MVC y las clases compiladas del backend;
no llama a proveedores externos ni requiere la base MySQL del usuario.

| Comprobacion | Deriva que detecta |
|---|---|
| Inventario y sesion | Una operacion de negocio agregada, retirada o con otro metodo/ruta; operationId repetido; handler incorrecto; grupo de sesion Bearer equivocado o ausente |
| Parametros | Nombre, ubicacion path/query, tipo, obligatoriedad o valor predeterminado distinto del handler real; parametros ficticios o faltantes |
| DTO y enums | Campos requeridos o tipos documentados distintos de los registros Java, incluidos elementos de listas y registros anidados; valores de enum distintos; esquema de exito incompatible con el retorno tipado del controlador |

Las extensiones `x-java-types`, `x-java-enum` y `x-java-handler` mantienen la
trazabilidad entre el contrato y sus tipos/rutas. Se deben actualizar al
refactorizar. `x-permisos-requeridos` describe las autoridades administrativas
revisadas en `SeguridadConfiguracion`; las pruebas funcionales de permisos
siguen en los contratos e integraciones existentes.

Los controles recorren las 41 operaciones explícitas de negocio y los
64 registros y 16 enums enlazados. La consulta de salud pertenece a Actuator:
se documenta aparte y mantiene su prueba HTTP existente.

## Ejecucion desde CMD

Desde el backend, comprobar solo la documentacion:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd "-Dtest=DocumentacionApiTests" test
```

Para incluir toda la regresion:

```bat
mvnw.cmd test
```

La compilacion y empaquetado completos siguen disponibles con
`mvnw.cmd clean verify`, cuando el JAR de destino no este siendo utilizado.
Las pruebas de documentacion leen `documentacion/openapi.json` desde el
directorio del modulo. Conservar el archivo junto con los tests al copiar
o ejecutar el proyecto.

## Validacion realizada

El 18 de noviembre de 2026 se ejecuto la clase nueva: 3 pruebas, sin fallos,
errores ni omitidas. Despues se ejecuto `clean verify` del backend: 562 pruebas,
sin fallos, errores ni omitidas, y `BUILD SUCCESS` con empaquetado del JAR.
Se utilizo Java 21.0.13 y el compilador Eclipse ECJ 3.38.0 para Java 21 en
este entorno de validacion; no se modifico el POM versionado del proyecto.

El JSON se contrasto adicionalmente con los modelos OpenAPI de FastAPI
0.141.1, sin agregar dependencias al backend. Los 100 esquemas se comprobaron
con JSON Schema 2020-12, se resolvieron las 496 referencias internas y se
validaron 16 ejemplos contra sus esquemas, incluidos sus formatos.
Estas comprobaciones complementan los tests Java; no se presentan como
una certificacion de cualquier herramienta cliente de OpenAPI.

El resumen reproducible, las huellas de los archivos y el resultado de Maven
estan en [la evidencia de documentacion](evidencias/documentacion-api-2026-11-18.json).
La guia de uso incluye un bloque PowerShell para consulta local: su sintaxis
se comprueba por separado y su ejecucion requiere los servicios y los datos
de acceso del entorno del usuario.

## Alcance

Los esquemas documentan tipos, campos, limites y estados observados en el
codigo. Las reglas dependientes de datos, las transiciones, la autenticacion,
los permisos efectivos y el aislamiento requieren las pruebas funcionales
existentes. Un archivo OpenAPI no sustituye esas comprobaciones.

Esta entrega centraliza documentacion y agrega controles de deriva. No
introduce rutas de negocio, cambios de seguridad, migraciones ni dependencias
de ejecucion. La evidencia de carga y la reserva de EQ-02 conservan su alcance.
La regresion y revision final de requisitos del bloque 9 corresponde a la
parte 5.

## Ampliación de lecturas de intervalo

El 19 de noviembre se añadió `tipoMedicion` y se ampliaron los motivos de
consumo. El contrato actual tiene 101 esquemas, 62 registros y 15 enums
enlazados; la regresión de la ampliación tiene 602 pruebas aprobadas.
La validación de 100 esquemas y 562 pruebas descrita antes conserva su
condición de registro histórico de la parte 4. La evidencia actual está en
[lecturas de intervalo](evidencias/lecturas-intervalo-2026-11-19.json).

## Ampliación de comparación

RF20 añade una operación, dos DTO y un enum. El contrato actual tiene
41 operaciones de negocio y una de salud, 104 esquemas, 64 registros y
16 enums enlazados. Los tres controles de documentación pasaron dentro de
la regresión de 632 pruebas. Las validaciones anteriores conservan su
fecha y alcance; la evidencia actual está en
[comparación de costos](evidencias/comparacion-costos-2026-11-19.json).


## Corrección de sincronización de tarifas

La incorporación y el refresco automáticos no añaden rutas ni cambian los
DTO públicos. Los tres controles de OpenAPI pasaron dentro de la nueva
regresión de 656 pruebas. El contrato conserva 41 operaciones de negocio,
una de salud, 104 esquemas, 64 registros y 16 enums enlazados.
La [guía de sincronización](sincronizacion-tarifas.md) documenta la migración,
el calendario persistido y la comprobación operativa.
