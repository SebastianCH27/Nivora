# Escalabilidad controlada del cierre del backend

P05/EQ-02 y nueva comprobación de EQ-01, 19 de noviembre de 2026.
La configuración ampliada sostiene 5000 usuarios durante 1800 segundos y
cumple los límites en las ocho operaciones. La configuración inicial falla
desde el primer escalón probado de 500 usuarios; su capacidad sostenible
exacta por debajo de ese escalón no se midió con este conjunto de datos.

## Condiciones controladas

Se ejecutaron ambos perfiles consecutivamente en el mismo entorno Linux.
Solo cambia la afinidad compartida del backend y MySQL: un núcleo frente
a cuatro. Permanecen iguales el JAR, bibliotecas, heap del backend de 512 MiB,
pool de 20 conexiones, 200 hilos HTTP y buffer InnoDB de 128 MiB.
El generador dispone de otros dos núcleos y heap de 768 MiB; la fuente de
uno distinto y heap de 128 MiB. La JVM del backend mantiene
`ActiveProcessorCount=4` en ambos perfiles para conservar su configuración.

Se conservan las mismas 5000 sesiones y los datos iniciales: 5000 suministros,
20000 lecturas, mitad acumuladas y mitad de intervalo, tarifas y recibos.
Entre perfiles se detienen los servicios y se restablece ese conjunto sin
renovar las sesiones ni las fechas iniciales. La fuente ofrece los mismos
eventos ficticios. Las actualizaciones y alertas permanecen activas, incluida
la sincronización automática de tarifas. MySQL es 8.0.46; Java es Temurin
21.0.13. Las herramientas Java se compilaron para Java 21 con ECJ 3.38.0.

El JAR medido tiene SHA-256 `789f65e1d37c1c10a9430eb380c5ddc239a4c212bf56a07287049636121cc3dc`.
El JAR reconstruido al finalizar conserva idénticas las 420 entradas de
clases/recursos de producción y sus 63 bibliotecas. El hash del ZIP/JAR
completo puede cambiar al reconstruir sus metadatos.

Cada usuario realiza una petición a la vez y espera 1000 ms tras la respuesta.
Es una carga cerrada de usuarios activos, incluidos sus tiempos de espera.
La medición excluye rampa y calentamiento. Las primeras tres etapas usan
rampa de 5 s, calentamiento de 10 s y medición de 60 s. La última usa 60 s
de rampa y 15 s de calentamiento; el perfil inicial se mide 120 s y el
ampliado 1800 s. El inicial ya incumple los límites; los tres escalones
anteriores permiten comparación con la misma duración.

## Resultados

| Perfil | Núcleos servidor/BD | Usuarios | Medición | Solicitudes | Peor p95 | Errores | Cumple etapa y continuidad |
|---|---:|---:|---:|---:|---:|---:|---|
| inicial | 1 | 500 | 60 s | 13,554 | 3320 ms | 0 | False |
| inicial | 1 | 1000 | 60 s | 22,310 | 3644 ms | 3 | False |
| inicial | 1 | 2500 | 60 s | 25,343 | 7232 ms | 106 | False |
| inicial | 1 | 5000 | 120 s | 61,011 | 10000 ms | 5378 | False |
| ampliado | 4 | 500 | 60 s | 29,854 | 12 ms | 0 | True |
| ampliado | 4 | 1000 | 60 s | 59,684 | 22 ms | 0 | True |
| ampliado | 4 | 2500 | 60 s | 107,988 | 650 ms | 0 | True |
| ampliado | 4 | 5000 | 1800 s | 3,437,894 | 1998 ms | 0 | True |

| Operación, ampliado 5000 | Solicitudes | p95 hasta | Errores |
|---|---:|---:|---:|
| consumo | 429,728 | 1957 ms | 0 |
| historial | 429,725 | 1962 ms | 0 |
| estadisticas | 429,715 | 1960 ms | 0 |
| costos | 429,687 | 1998 ms | 0 |
| proyeccion | 429,728 | 1983 ms | 0 |
| recibos | 429,751 | 1964 ms | 0 |
| notificaciones | 429,795 | 1953 ms | 0 |
| comparacion | 429,765 | 1982 ms | 0 |

Los percentiles se redondean hacia arriba al milisegundo. Se exige al menos
100 muestras por operación, p95 ≤ 2000 ms, menos de 1 % de errores internos
o transporte y cero errores restantes. Cada etapa aprobada también exige
lecturas nuevas, alertas y sincronización de tarifas dentro de la ventana
exacta de medición. Los JSON conservan sus contadores y fechas.

El peor p95 queda solo 2 ms por debajo del límite. El resultado aprueba
este recorrido, con margen estrecho; no demuestra holgura para más carga
ni garantiza repetirlo con otros recursos. El despliegue elegido requiere
su propia medición y dimensionamiento.

## Reproducir y consultar la evidencia

`herramientas/EvaluarEscalabilidad.py` requiere Linux, JDK 21, MySQL 8,
`taskset` y al menos siete CPU disponibles. Construir antes el JAR con
`mvnw clean verify`; ejecutar desde el módulo:

```bash
python3 herramientas/EvaluarEscalabilidad.py --salida /ruta/nueva/resultados
```

Acepta rutas explícitas mediante `--java`, `--javac`, `--mysqld`,
`--mysql-basedir` y `--mysql-libdir`. `--rapida` solo valida el montaje.
La ejecución utiliza una instancia MySQL, cuenta y base desechables en
loopback; las elimina al terminar. Las credenciales y el CSV de sesiones
no forman parte de los informes compartidos.

Los [JSON completos](evidencias/cierre-backend-2026-11-19/resumen.json),
los informes por operación, evaluaciones y muestras de CPU/RSS/afinidad de
cada perfil se conservan en `evidencias/cierre-backend-2026-11-19/`.
`recursos-resumen.json` excluye rampa/calentamiento; 100 % de CPU representa
un núcleo ocupado. La memoria reportada es RSS del proceso.

En Windows se conserva `EjecutarPruebaCarga.ps1 -DatosMixtos`, ver
[procedimiento de carga](verificacion-rendimiento.md). Ese recorrido no
controla por sí solo la ampliación de CPU de este experimento.

La evidencia acredita estos escenarios del backend en el montaje medido.
No mide creación masiva de sesiones contra identidad, entrega visual Push,
latencia de una red pública ni capacidad garantizada de la laptop o de
otro despliegue. HTTPS tiene su [evaluación separada](verificacion-https.md).
