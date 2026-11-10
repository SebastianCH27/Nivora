# Sincronizacion automatica de lecturas

La tarea programada de Nivora utiliza `RecuperarLecturasConCorte` para los
suministros registrados en su tabla `suministros`, con la fuente configurada
`empresa-agua`. No recorre los 100 000 suministros de la Empresa si no estan
registrados en Nivora. Las consultas futuras del usuario siguen separadas de
la actualizacion externa, segun ADR-003.

## Preparar e iniciar

Con los scripts 01-05 de Nivora aplicados, ejecutar como administrador en
`mysql>` antes de iniciar el servidor con la tarea habilitada:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/06-sincronizacion-lecturas.sql;
SHOW TABLES FROM nivora;
```

El script agrega `programacion_sincronizacion_lecturas`. Puede repetirse y
conserva lecturas, contextos, incidencias, progresos y programaciones existentes.
La cuenta `nivora_app` utiliza los permisos DML ya asignados sobre `nivora.*`.
El backend no crea ni modifica la estructura automaticamente.

Iniciar la Empresa de Agua en el puerto 8081. Desde CMD, en
`tecnologia/nivora-backend`, iniciar Nivora:

```bat
powershell -NoProfile -Command "$claveNivora = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $claveNivora).Password; & '.\mvnw.cmd' spring-boot:run"
```

Nivora permanece en el puerto 8082 y ejecuta la tarea en un hilo programado.
Cada proceso necesita su propia variable de contrasena. La consola del
servidor debe permanecer abierta mientras se quiera mantenerlo ejecutando.
El arranque con `--spring.main.web-application-type=none` conserva las
comprobaciones manuales y no activa la tarea automatica.

## Ciclos y reintentos

1. Registrar los suministros propios que todavia no tengan programacion.
2. Seleccionar una tarea vencida y reservarla en una transaccion corta.
3. Ejecutar la recuperacion existente fuera de esa transaccion, con su limite
   de paginas y su contexto persistido.
4. Guardar el resultado y la proxima fecha si la reserva sigue vigente.
5. Continuar hasta alcanzar el maximo de intentos del ciclo.

Un ciclo solo selecciona fechas elegibles al comenzar. Las siguientes
fechas de una tarea ya procesada quedan para ciclos posteriores. Una
reserva vencida puede recuperarse incluso dentro del mismo ciclo si aun
queda capacidad; el maximo cuenta intentos, no codigos distintos.

| Resultado | Programacion |
|---|---|
| `RECORRIDO_COMPLETO` | Restablece fallos y espera el intervalo completo. Actualiza la fecha de exito automatico. |
| `EN_CURSO` | Restablece fallos y continua pronto desde el mismo cursor y contexto. |
| `ERROR_FUENTE` | Conserva datos y progreso; programa reintento creciente. |
| `SUMINISTRO_NO_ENCONTRADO` | Conserva la informacion propia; programa reintento creciente. |
| `CONFLICTO` | Otra ejecucion modifico el progreso; reintenta pronto sin contarlo como fallo de fuente. |
| `ERROR_ALMACENAMIENTO` o `ERROR_INTERNO` | Programa reintento creciente cuando el almacenamiento de la programacion este disponible. |
| `BLOQUEADO` | Pausa la tarea dejando la proxima fecha en `NULL`; requiere revision. |

El retraso crece desde 30 segundos a 60, 120 y asi sucesivamente hasta 1800.
El contador y la fecha se guardan en MySQL; no se utilizan esperas bloqueantes
para el reintento ni se reinician al volver a abrir Nivora. Un recorrido parcial
conserva el corte y el inventario, tambien despues de un fallo. Los recorridos
completados mantienen la revision completa del historial en el siguiente
ciclo, bajo un nuevo corte, con la deduplicacion existente.

Un fallo de un suministro no impide intentar los otros seleccionables.
Cuando falla la programacion misma, se conserva la reserva hasta su
vencimiento; el siguiente ciclo podra recuperarla al restablecer MySQL.

## Reservas y ejecuciones concurrentes

La reserva tiene un token y un vencimiento. Su adquisicion usa bloqueo de
fila y actualizacion condicional; no mantiene una transaccion SQL durante
las llamadas HTTP. La confirmacion exige el mismo token y una reserva aun
vigente. Un propietario anterior no sobrescribe la programacion de uno nuevo.

El vencimiento permite recuperar el trabajo tras un cierre inesperado.
Si una ejecucion supera el plazo, puede repetirse trabajo de red; la
identificacion de lecturas y las revisiones del progreso mantienen las
protecciones de incorporacion. No se garantiza una unica llamada HTTP por
pagina. Los servidores que compartan programacion necesitan relojes coherentes.
La capacidad con varias instancias y 5000 usuarios requiere sus pruebas
posteriores; los limites iniciales no acreditan esa capacidad.

El operador puede consultar el estado con el comprobador manual existente.
Si un recorrido esta `BLOQUEADO`, debe revisar su incidencia y corregir la
causa antes de usar la operacion `reiniciar`. Al observar que el progreso
ya no esta bloqueado, un ciclo posterior reactiva su programacion pausada.
La tarea no reinicia por si sola un recorrido bloqueado ni borra sus datos.

## Configuracion inicial

| Propiedad bajo `nivora.sincronizacion` | Valor |
|---|---|
| `habilitada` | `true` |
| `espera-inicial-ms` | `5000` |
| `intervalo-ms` | `10000` |
| `maximo-intentos-por-ciclo` | `2` |
| `limite-pagina` | `100` |
| `maximo-paginas-por-intento` | `2` |
| `reserva-segundos` | `180` |
| `intervalo-completo-segundos` | `300` |
| `continuacion-segundos` | `10` |
| `reintento-inicial-segundos` | `30` |
| `reintento-maximo-segundos` | `1800` |

El planificador espera diez segundos despues de terminar cada ciclo.
Las fechas de elegibilidad son minimas: el tiempo de procesamiento y las
otras tareas pendientes tambien afectan la frecuencia real. No se promete
actualizar todos los suministros exactamente cada cinco minutos. Una reserva
debe dimensionarse para el trabajo previsto y los tiempos de red y base.
Las paginas por intento se limitan a 1-10; cada pagina admite 1-500 lecturas.
Las duraciones de la politica admiten segundos enteros positivos hasta un dia.

Para desactivar la tarea, usar `nivora.sincronizacion.habilitada=false`.
El perfil `test` la desactiva; las pruebas no consultan la Empresa real ni
necesitan su MySQL. La verificacion habitual es:

```bat
mvnw.cmd clean verify
```

## Comprobar el avance

Los mensajes `Sincronizacion: suministro ...` informan el resultado, los
conteos del intento y la proxima fecha en UTC. Una reserva sin confirmar se
informa por separado; el mensaje no certifica un nuevo calendario guardado.
Los detalles de excepciones quedan disponibles en nivel DEBUG. Un fallo
del ciclo se registra sin terminar el servidor.

En `mysql>`, consultar:

```sql
SELECT codigo_suministro, ultimo_resultado, fallos_consecutivos, ultimo_intento_utc, ultimo_exito_utc, proximo_intento_utc, reserva_hasta_utc FROM nivora.programacion_sincronizacion_lecturas;
```

`ultimo_exito_utc` representa un recorrido automatico completo, no la fecha
de la ultima medicion ni la fecha confirmada por la fuente. Esos datos se
consultan en las lecturas y el contexto del progreso. Un resultado parcial
no adelanta la fecha de exito. Las consultas de supervision mediante la API
y su presentacion a usuarios se implementaran en el siguiente avance.

Con la Empresa apagada y MySQL disponible, Nivora sigue ejecutandose en
8082 y conserva las lecturas propias. `/actuator/health` comprueba el servidor
y su conexion de datos; no certifica disponibilidad ni actualidad de la fuente.

## Validacion

La suite contiene 231 pruebas: las 200 anteriores y 31 nuevas de politica,
programacion JDBC y trabajador. Cubren registro idempotente, suministros
nuevos, reservas concurrentes y vencidas, calendario entre instancias,
reintentos limitados, aislamiento de fallos, pausa y reinicio manual,
limite por ciclo y continuacion del mismo corte.

Se comprobo ademas con MySQL 8.0.46 y las dos aplicaciones Spring reales:
paginacion automatica, publicaciones posteriores, caida y restablecimiento
de la Empresa, disponibilidad de Nivora durante la caida, conservacion de
datos y calendario tras reiniciar y recuperacion automatica sin duplicados.
