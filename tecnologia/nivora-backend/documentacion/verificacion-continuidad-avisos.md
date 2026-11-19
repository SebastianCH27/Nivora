# Continuidad de consultas y avisos

La primera parte del bloque 9 amplía la verificación de EQ-04, RF24–RF26
y RF29. Usa sesiones de consulta, las rutas HTTP de Nivora, los casos de
evaluación y despacho y los repositorios JDBC. La fuente de agua e identidad
y el emisor de notificaciones se controlan en las pruebas.

## Preparación del caso

El suministro ficticio `000101` tiene lecturas que producen 2000 L en
octubre y 2200 L acumulados en noviembre de 2026. Una sesión de consulta
registra por HTTP el permiso y la suscripción de su dispositivo. Se genera
la alerta de aumento de consumo y se guarda un recibo de octubre por
S/ 13,46. El caso registra su aviso de disponibilidad.

El primer despacho recibe un resultado temporal para cada aviso. El emisor
indica que se reintente desde 90 segundos después del primer intento.
El reloj controlado permite comprobar ese límite.

## Matriz comprobada

| Caso | Resultado |
| --- | --- |
| Fallo temporal del emisor con Empresa de Agua disponible. | Ambas entregas quedan pendientes. Las siete consultas conservan su respuesta para los mismos datos y fecha de consulta. |
| Fallo temporal del emisor con Empresa de Agua e identidad caídas. | La sesión vigente sigue consultando el almacenamiento local. Los contadores de las fuentes confirman que las consultas se resuelven en Nivora. |
| Recreación del repositorio y del despachador con la misma base. | Recuperan las dos entregas y su calendario. A los 89 segundos todavía hay un intento por entrega. |
| Recuperación del emisor al cumplirse los 90 segundos. | Cada entrega queda aceptada en su segundo intento. Un nuevo ciclo conserva dos entregas y cuatro envíos totales. La bandeja y el recibo mantienen sus datos. |
| Desactivación del dispositivo desde la API. | Los siguientes ciclos dejan de enviar sus avisos pendientes. La baja de la suscripción limpia las entregas y la bandeja conserva los dos avisos. |
| Seis fallos temporales consecutivos. | La entrega termina en `FALLIDA`, conserva seis intentos y permanece como un solo registro después de nuevos ciclos. |
| Reserva vencida y sustituida por otro trabajador. | El token antiguo pierde vigencia. La confirmación antigua conserva la entrega pendiente y la reserva nueva puede confirmar un único intento aceptado. |

Las siete consultas comparadas son consumo actual, historial, estadísticas,
costos, proyección, recibos y bandeja de notificaciones. Se comprueba HTTP 200
y `Cache-Control: no-store` en cada respuesta.

La recreación usa nuevas instancias del repositorio y despachador para
comprobar el estado guardado. La aceptación simulada del emisor confirma el
avance de la cola. La recepción visual en Windows se comprueba con la
página y el flujo descritos en [Web Push](web-push.md).

## Repetir en Windows

Detén el proceso del JAR de Nivora con Ctrl+C y ejecuta desde
`tecnologia\nivora-backend` la suite completa.

```cmd
mvnw.cmd clean verify
```

También puedes repetir solo los tres casos HTTP de continuidad y baja.

```cmd
mvnw.cmd "-Dtest=AccesoConsultaIntegradoTests#consultasYBandejaContinuanDuranteFallosDePushYDeLaEmpresa+desactivarUnDispositivoDetieneLosReintentosYConservaLaBandeja" test
```

Para revisar los reintentos y las reservas de la cola, usa esta línea.

```cmd
mvnw.cmd -Dtest=ColaWebPushTests test
```

La suite utiliza H2 con esquemas de prueba. Los tres casos HTTP también se
ejecutaron con MySQL 8.0.46 en una base aislada. El perfil de prueba mantiene
desactivados los procesos automáticos y los casos invocan sus ciclos con
un reloj controlado.

## Ampliación de RF20

Los dos casos de continuidad también consultan `/comparacion` antes y
durante el fallo. La lista actual tiene ocho consultas; la matriz anterior
describe las siete del corte original. Se mantienen los datos, la cobertura
y la selección de tarifas de la nueva respuesta. Ver
[la evidencia de comparación](evidencias/comparacion-costos-2026-11-19.json).
