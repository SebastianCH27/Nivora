# Revisión final del backend: bloque 9

Parte 5, 19 de noviembre de 2026, zona America/Lima.
Base de la revisión: contenido publicado como `6cc8260`.

Este registro conserva el corte anterior a las ampliaciones de RF10 y RF20.
La [matriz actual](matriz-requisitos-backend.md) y las evidencias enlazadas
al final identifican sus avances y la regresión posterior.

La regresión del backend está aprobada en las 562 pruebas ejecutadas.
La documentación API y la carga disponen de evidencias. La revisión de
requisitos conserva brechas funcionales y evaluaciones pendientes; el
producto completo todavía requiere las interfaces y su despliegue evaluado.

## Entregas del bloque

| Parte | Entrega | Registro publicado |
|---|---|---|
| 1 | [Continuidad de consultas y avisos](verificacion-continuidad-avisos.md) | `00748bb` |
| 2 | [Contratos HTTP y aislamiento](verificacion-contratos-api.md) | `ce72c35` |
| 3 | [Herramientas y evidencia de carga](resultados-carga-github-actions.md) | `2d43391`, `4fd156a` |
| 4 | [Documentación API y controles de deriva](verificacion-documentacion-api.md) | `6cc8260` |
| 5 | [Revisión de requisitos y reservas](matriz-requisitos-backend.md) | `cc65d63` |

La quinta parte deja trazado el resultado de la verificación. Cada reserva
mantiene su estado aunque los entregables del bloque estén registrados.

## Regresión utilizada

| Procedencia | Ejecución | Resultado |
|---|---|---|
| Entorno de preparación de la parte 4 | `clean verify`, Java 21.0.13 y ECJ 3.38.0, perfil de pruebas con H2 y fuentes HTTP controladas | 562 pruebas en 32 clases, cero fallos, errores u omitidas; `BUILD SUCCESS` y JAR empaquetado. |
| Windows del responsable, 19 de noviembre a las 00:07 | Salida Maven compartida en el chat al completar la parte 4 | 562 pruebas, cero fallos, errores u omitidas; `BUILD SUCCESS`. |

El [resumen de API](evidencias/documentacion-api-2026-11-18.json) conserva
la ejecución de preparación. El [registro de esta revisión](evidencias/revision-final-backend-2026-11-19.json)
consolida los informes Surefire de esa ejecución, las referencias de la
matriz y sus límites. No se presenta como una nueva ejecución de Maven.
Esta entrega cambia documentación; conserva las fuentes, pruebas, POM,
configuración y migraciones de la referencia revisada.

Las pruebas son del backend. La suite completa de Empresa de Agua y las
pruebas de interfaces no se ejecutaron nuevamente para esta revisión.
Los informes de carga conservaron las 559 pruebas de su JAR original;
la cifra 562 incorpora después los tres controles de documentación.

## Resultado de la carga

El perfil completo con pool 20 sostuvo 5000 sesiones durante 30 minutos:
3 375 041 consultas, p95 por operación entre 1793 y 1867 ms y cero errores.
Hubo actualización de lecturas y evaluación de alertas durante la medición.
EQ-01 queda verificado para el montaje y mezcla documentados.

EQ-02 permanece parcial: las ejecuciones con pools 10 y 20 usaron VMs
distintas. Su comparación no demuestra por sí sola escalabilidad de CPU,
memoria o instancias. Las reservas, recursos y límites se conservan en
[los resultados de carga](resultados-carga-github-actions.md).

## Requisitos revisados

La [matriz](matriz-requisitos-backend.md) conserva el estado vigente.
En el corte histórico descrito aquí contenía una fila para cada uno de
los 39 RF y los 10 escenarios EQ, con evidencia y reservas. Entre las
brechas están el procesamiento de lecturas de intervalo (RF10), la
diferencia de costos entre periodos (RF20) y los gráficos del frontend
(RF21). En ese corte también faltaban evaluaciones de HTTPS, escalabilidad controlada,
contratos externos completos, sustitución de adaptador y uso desde los
dispositivos finales.

En ese corte, el backend de la demostración usaba lecturas acumuladas y
la API devolvía costos por periodo y diferencias de volumen. Las ampliaciones
posteriores completan intervalos y diferencias de costos, según los enlaces
del final. Las 562 pruebas de este registro conservan su alcance histórico.

## Repetir o ampliar las comprobaciones

Desde CMD en el backend, una regresión que no requiere los servicios
locales de MySQL ni Empresa de Agua encendidos:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd test
```

Para construir y empaquetar también el JAR, detener antes su proceso si
Windows lo mantiene en uso y ejecutar `mvnw.cmd clean verify`.
Para otra prueba de carga, conservar el JAR, configuración, mezcla y datos
del escenario a evaluar y seguir [la guía de Actions](carga-github-actions.md).
Repetir la carga completa corresponde a un cambio que justifique evaluarla.

Los avances posteriores completan [P01: intervalos](lecturas-intervalo.md)
en `cf2f699` y [P02: comparación de costos](comparacion-costos.md) en la
entrega siguiente. Este documento conserva el corte del bloque 9; la
[matriz actual](matriz-requisitos-backend.md) identifica las reservas
restantes de interfaces, despliegue y evaluaciones de calidad.


La [validación posterior sobre 890db94](cierre-validacion-backend.md)
actualiza HTTPS, contratos, sustitución y escalabilidad. Este documento
conserva los resultados históricos de su corte original.
