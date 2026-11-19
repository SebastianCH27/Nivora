# Rendimiento y carga de consultas

Bloque 9, parte 3. Procedimiento reproducible para EQ-01 y EQ-02.
Las metas completas siguen pendientes hasta conservar las mediciones de
5000 usuarios durante 1800 segundos y comparar dos configuraciones de recursos.

Para ejecutar el montaje fuera de la laptop, seguir
[Carga en GitHub Actions](carga-github-actions.md). El flujo manual prepara
una VM temporal, mide sus recursos y publica los JSON permitidos en sus logs.
Su trabajo solo arranca en un repositorio publico.

## Entorno y datos

Desde el backend, `herramientas/EjecutarPruebaCarga.ps1` compila dos herramientas
con el JDK 21 instalado y extrae el controlador MySQL del JAR empaquetado.
Requiere MySQL 8 y una cuenta administradora para crear y retirar una base
`nivora_carga_<identificador>` y su cuenta temporal con permisos de datos.
La clave se solicita de forma oculta. La cuenta administrativa se utiliza
solo durante la preparacion y la limpieza.

El montaje usa los scripts 02 a 20 sobre esa base nueva. Inicia una fuente
ficticia en `127.0.0.1:28081` y una instancia del JAR en `127.0.0.1:28082`.
Al finalizar detiene sus procesos y retira su base y cuenta temporales.
Conserva los informes en `target/carga/<identificador>/`.

Por cada usuario se crean un suministro ficticio, una sesion independiente,
cuatro lecturas acumuladas que cubren tres periodos, una tarifa completa,
un recibo y un aviso de disponibilidad. La fuente ofrece una quinta lectura
por suministro; el sincronizador habitual la incorpora mientras se consulta.
El incremento del consumo permite generar una alerta por suministro.

Las sesiones se preparan directamente para medir las consultas autenticadas.
Duran cuatro horas en este montaje. La capacidad de validar nuevos accesos
con proveedores de identidad requiere una prueba independiente.
La entrega Web Push queda deshabilitada en esta instancia de carga.

## Ejecutar desde CMD

Copiar las carpetas del ZIP sobre la raiz de Nivora. Si hace falta reconstruir
el JAR, detener primero la instancia que lo utiliza y ejecutar
`mvnw.cmd clean verify`. Para medir, conservar MySQL encendido y detener
otras cargas de trabajo que puedan distorsionar el resultado.

Prueba breve: 20 usuarios, rampa de 5 segundos, calentamiento de 5 segundos
y medicion de 45 segundos. Ejecutar en un CMD de trabajo:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
powershell -NoProfile -ExecutionPolicy Bypass -File .\herramientas\EjecutarPruebaCarga.ps1
```

Introducir la clave de `root` cuando se solicite. El proceso abre y comprueba
su propio entorno. Para otra cuenta administrativa, agregar
`-AdministradorMysql nombre`; para otro puerto MySQL, `-PuertoMysql numero`.

Para ejecutar las etapas de EQ-01 y EQ-02 en el equipo destinado a la medicion:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File .\herramientas\EjecutarPruebaCarga.ps1 -Completa
```

| Usuarios | Rampa | Calentamiento | Medicion estable |
|---|---:|---:|---:|
| 500 | 60 s | 15 s | 120 s |
| 1000 | 60 s | 15 s | 120 s |
| 2500 | 60 s | 15 s | 120 s |
| 5000 | 60 s | 15 s | 1800 s |

La ejecucion completa dura aproximadamente 41 minutos mas el arranque.
Se detiene al finalizar la primera etapa que incumpla los umbrales para
permitir revisar el cuello de botella antes de aumentar la carga.
Un equipo que falla en una etapa conserva ese resultado como evidencia.

Para comparar recursos, repetir el mismo perfil cambiando recursos medibles.
El script acepta `-MemoriaBackendMb`, `-MemoriaGeneradorMb`, `-PoolConexiones`
y `-HilosHttp`; registra los valores aplicados. Los valores iniciales son
512 MB de heap por JVM principal, 10 conexiones y 200 hilos HTTP.
La fuente tiene un limite de heap de 128 MB. MySQL requiere memoria adicional.

La ampliacion debe demostrar una carga sostenible mayor. Aumentar un parametro
por si solo no acredita EQ-02. Conservar la configuracion y los recursos de
MySQL, CPU, RAM, procesos concurrentes y condiciones de ambas ejecuciones.
El generador comparte equipo con la aplicacion en este montaje: si consume
la capacidad disponible, repetir en infraestructura apropiada antes de
atribuir el limite al backend. El modo local permite reproducir y explorar
la carga; una evaluacion de despliegue debe incluir su topologia real.

## Mezcla y medicion

Cada usuario virtual reutiliza su sesion y realiza una consulta a la vez.
Rota entre las siete operaciones, espera 1000 ms despues de cada respuesta
y repite hasta terminar. Los usuarios comienzan en momentos distribuidos
durante la rampa; sus primeras operaciones tambien estan distribuidas.
La carga es cerrada: si crece la latencia, disminuye el ritmo de solicitudes.
Los 5000 usuarios activos incluyen el tiempo de espera entre consultas.

| Operacion | Ruta bajo `/api/v1/acceso/consulta` |
|---|---|
| Consumo actual | `/consumo-actual` |
| Historial | `/historial?meses=2` |
| Estadisticas | `/estadisticas?meses=2` |
| Costos | `/costos?meses=2` |
| Proyeccion | `/proyeccion` |
| Recibos | `/recibos?limite=12` |
| Notificaciones | `/notificaciones?limite=20` |

Las solicitudes iniciadas durante la medicion estable se incluyen hasta
recibir su respuesta o agotar el plazo. La rampa y el calentamiento se excluyen.
Los tiempos incluyen la espera del cliente, la conexion y la respuesta completa.
Cada solicitud tiene un plazo de 10 segundos y la conexion uno de 5 segundos.
Los percentiles utilizan un histograma con redondeo hacia arriba al milisegundo;
la cubeta de desbordamiento utiliza el maximo observado como cota conservadora.

Para cada operacion, `cumple` exige al menos 100 muestras, p95 <= 2000 ms,
menos del 1 % de respuestas 5xx o fallos de transporte y cero errores restantes.
El minimo de muestras y el requisito de cero errores restantes son controles
de esta herramienta adicionales a la meta de EQ-01.
Una respuesta 200 se comprueba por tipo JSON y presencia del codigo del
suministro esperado. Los 4xx, el contenido inesperado y el suministro ajeno
se cuentan como otros errores. La matriz de contratos de la parte 2 conserva
la verificacion detallada del cuerpo y del aislamiento.

## Evidencia a conservar

- `entorno.json`: equipo, JVM, huella SHA-256 del JAR, recursos, datos y perfil.
- `etapa-*-<usuarios>.json`: fechas, usuarios, duracion, muestras, latencias,
  errores, rendimiento y recursos del generador.
- `etapa-*-recursos.csv`: CPU y memoria del proceso backend cada segundo.
  El 100 % de CPU representa un nucleo ocupado; puede superar 100 %.
- `etapa-*-antes.json` y `etapa-*-despues.json`: almacenamiento,
  sincronizaciones y alertas. Las fechas se contrastan con el intervalo exacto
  de medicion; se cuentan las lecturas y alertas registradas dentro de el.
- `etapa-*-evaluacion.json`: limites de la etapa y continuidad observada.
- Logs del backend, fuente y generador para explicar errores.

`cubre5000Durante30Minutos` describe el perfil ejecutado. Para acreditar EQ-01
se necesitan ademas los umbrales por operacion, los 5000 usuarios mantenidos,
la continuidad de actualizaciones y evaluacion de alertas, y recursos suficientes
del generador. La comparacion de capacidad es evidencia adicional de EQ-02.

La quinta lectura es un evento nuevo por suministro. Para verificar actividad
continua, la programacion repite sincronizaciones cada 30 segundos y atiende
un suministro por ciclo de un segundo. El informe consulta el ultimo exito
persistido por suministro. Una alerta se guarda una sola vez por periodo;
en etapas posteriores puede haber evaluaciones exitosas sin alertas nuevas.
Interpretar esos datos junto con las sincronizaciones y los logs.

`usuarios.csv` contiene tokens ficticios del entorno temporal. Conservarlo en
`target/`, excluido de Git. Para compartir resultados, usar los informes
JSON/CSV de recursos y omitir ese archivo.
Si se cierra toda la consola a la fuerza, revisar los procesos y puertos
28081/28082. El nombre exacto de la base aparece en `entorno.json` y en la
salida de preparacion. Despues de detener los procesos de esa prueba,
retirar unicamente su base y su cuenta desde MySQL, sustituyendo
`IDENTIFICADOR` por el valor de esa ejecucion:

```sql
DROP DATABASE IF EXISTS nivora_carga_IDENTIFICADOR;
DROP USER IF EXISTS 'ncarga_IDENTIFICADOR'@'127.0.0.1';
```

## Estado de esta entrega

La regresion del backend conserva 559 pruebas, sin fallos ni errores.
Las herramientas de carga se verifican por separado mediante compilacion
y un recorrido contra MySQL 8.0.46 y el JAR real.
La comprobacion de consultas en Windows esta registrada al final de este
documento. Queda por repetir el recorrido completo con el supervisor corregido
y ejecutar la prueba completa de capacidad.


## Comprobacion breve del 18 de noviembre de 2026

Se ejecutaron dos etapas consecutivas de 20 y 100 usuarios, con 45 segundos
de medicion por etapa, rampa de 5 segundos, calentamiento de 5 segundos
y pausa de 1000 ms. Se mantuvieron todos los usuarios previstos.
La base contenia 100 suministros, 400 lecturas iniciales y 100 sesiones.
El backend, MySQL, la fuente y el generador compartian el entorno Linux.
Se usaron Temurin 21.0.13, MySQL 8.0.46 y PowerShell 7.4.6, con telemetria
desactivada antes de iniciar PowerShell. Las herramientas se compilaron
con ECJ 3.38.0 para Java 21. La ejecucion Java en Windows se comprobo posteriormente con el resultado
registrado en la seccion siguiente.

| Operacion | p95 hasta, 20 usuarios | p95 hasta, 100 usuarios |
|---|---:|---:|
| Consumo | 9 ms | 6 ms |
| Historial | 9 ms | 6 ms |
| Estadisticas | 10 ms | 7 ms |
| Costos | 15 ms | 9 ms |
| Proyeccion | 13 ms | 8 ms |
| Recibos | 9 ms | 5 ms |
| Notificaciones | 8 ms | 6 ms |

La primera etapa registro 895 solicitudes y la segunda 4485: 5380 en total,
con cero errores internos, de transporte o de contenido. En cada intervalo
estable se incorporaron 40 lecturas nuevas y se generaron 40 alertas.
La base termino con 500 lecturas y 100 alertas. La limpieza automatica retiro
la base y la cuenta temporales al terminar.

La segunda etapa comparte la JVM ya calentada por la primera. Los resultados
caracterizan este recorrido breve. Quedan pendientes los 5000 usuarios durante
30 minutos, las condiciones de despliegue y la comparacion de recursos.
El medidor tambien se comprobo con una fuente HTTP local que respondia 503,
otra que devolvia un codigo de suministro ajeno y otra que enviaba cabeceras
pero retenia el cuerpo. Clasifico los errores, rechazo los umbrales y limito
la espera del cuerpo a 10 segundos.

La evidencia con fechas, huellas, resultados y muestras de CPU/memoria esta en
[evidencias/rendimiento-breve-2026-11-18.json](evidencias/rendimiento-breve-2026-11-18.json).


## Comprobacion en Windows y correccion del supervisor

El 18 de noviembre de 2026, el generador ejecutado desde Windows completo
842 consultas con 20 usuarios mantenidos durante 45 segundos. Las siete
operaciones cumplieron los umbrales: cero errores, p95 entre 49 y 100 ms,
y `fallosGenerador=0`. El registro de errores estaba vacio.
El informe original esta en
[evidencias/rendimiento-windows-breve-2026-11-18.json](evidencias/rendimiento-windows-breve-2026-11-18.json).

El supervisor interrumpio el recorrido al comprobar `Process.ExitCode`.
El sintoma coincide con [PowerShell #5421](https://github.com/PowerShell/PowerShell/issues/5421):
Windows PowerShell puede devolver una salida nula para procesos con registros
redirigidos si no se conserva previamente su `Handle`.
El valor nulo se comparaba con cero y provocaba el aviso generico de fallo.
Como esa ejecucion no imprimio el valor de `ExitCode`, la causa concreta se
infiere del comportamiento observado y del problema documentado.

`Iniciar-Java` ahora conserva el handle de cada proceso al iniciarlo.
La comprobacion distingue salida nula, codigo distinto de cero e informe
ausente. Una salida desconocida sigue interrumpiendo la ejecucion; cada
codigo de error real aparece en el mensaje. Una salida correcta imprime
`Generador finalizado. Codigo de salida: 0.` antes de continuar.

Se verificaron seis casos del control de procesos: salida 0, salidas 2 y 7,
salida nula, informe ausente y una simulacion del handle diferido. Ademas,
el recorrido breve contra MySQL y el JAR real con el supervisor corregido
termino con codigo 0, guardo la evaluacion posterior y retiro el entorno temporal. La comprobacion definitiva de esta correccion en Windows queda
pendiente de repetir el comando habitual en el equipo del proyecto.
La ejecucion anterior no alcanzo a guardar el estado posterior de sincronizacion;
esa parte se comprobara en la repeticion. Las metas EQ-01 y EQ-02 conservan
sus mediciones completas pendientes.


## Comparacion de 10 y 20 conexiones en Windows

Se repitio el perfil completo con 20 conexiones. Ambas ejecuciones mantuvieron
2500 usuarios durante la etapa de 120 segundos y se detuvieron antes de 5000.

| Grupo de conexiones | Solicitudes en la etapa | p95 por operacion | Errores internos o de transporte |
|---|---:|---:|---:|
| 10 | 89 552 | 4038 a 4093 ms | 0 |
| 20 | 89 616 | 4110 a 4139 ms | 1 en costos: 0,0078 % de esa operacion |

En ambos casos el criterio incumplido fue p95 <= 2000 ms. El error registrado
con 20 conexiones permanecio por debajo del 1 % permitido. Los resultados
no muestran una mejora apreciable con 20 conexiones en estos ensayos.
La CPU del backend, medida solamente con 10 conexiones, promedio 1,38 nucleos
logicos de los cuatro disponibles. Su memoria residente promedio fue 296 MiB,
con un maximo de 328 MiB. Estas dos ejecuciones no incluyeron CPU del sistema,
heap ni esperas. El diagnostico posterior se describe al final de esta guia.

La evidencia descriptiva esta en
[evidencias/comparacion-carga-windows-2026-11-18.json](evidencias/comparacion-carga-windows-2026-11-18.json).
Las etapas de 500 y 1000 usuarios cumplieron los umbrales segun las salidas de
consola compartidas. La meta de 5000 usuarios durante 30 minutos sigue pendiente.

## Diagnostico de una etapa

El modo `-Diagnostico` registra una etapa instrumentada. `-SuministrosDatos`
permite preparar 5000 suministros aunque se midan 2500 usuarios.
Para investigar el punto donde aumentaron las latencias, ejecutar desde CMD:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File .\herramientas\EjecutarPruebaCarga.ps1 -Etapas 2500 -SuministrosDatos 5000 -DuracionSegundos 120 -RampaSegundos 60 -CalentamientoSegundos 15 -PoolConexiones 10 -Diagnostico
```

Dura aproximadamente cuatro minutos, incluido el arranque. El diagnostico
admite una sola etapa por ejecucion. Mantiene la mezcla, la pausa entre
consultas y los umbrales. La JVM empieza de nuevo: esta ejecucion focalizada
sirve para investigar; la validacion formal requiere repetir el perfil completo
cuando se haya elegido el cambio correspondiente.

Al iniciar las JVM del backend de prueba y del generador se carga el agente
`DiagnosticoCarga.java`. Consulta las metricas locales cada cinco segundos:

- Conexiones ocupadas, libres y totales de HikariCP, e hilos que esperan una conexion.
- Hilos HTTP ocupados y limite configurado de Tomcat.
- CPU del sistema, memoria fisica libre y memoria heap de cada JVM.
- Cantidad y tiempo acumulado de las colecciones de basura.

El script muestrea tambien CPU y memoria de los procesos llamados `mysqld`.
El CSV incluye sus PID; si hay varios servidores MySQL, esa cifra agrega todos
los encontrados. Un proceso no accesible puede dejar la metrica ausente.
El agente es local a las JVM de prueba y utiliza sus MBeans registrados; los
valores se consultan dentro del proceso. La configuracion se aplica al arrancar
el JAR de carga. Se conserva el mismo numero de tres procesos Java del montaje.

Cada ejecucion deja:

- `etapa-1-2500-diagnostico.json`: resumen del intervalo estable, con latencias
  de las siete operaciones y recursos. Su contenido se muestra tambien en consola.
- `diagnostico-backend/jvm.csv` y `diagnostico-generador/jvm.csv`: muestras originales.
- `diagnostico-backend/hilos-1.txt` a `hilos-3.txt`: hasta tres capturas, separadas
  por al menos 20 segundos y solicitadas durante la medicion. Incluyen hilos de
  plataforma y hasta 12 marcos por hilo; los hilos virtuales del generador no
  aparecen como hilos de plataforma en esas capturas.
- `backend-gc.log` y `generador-gc.log`: registros de GC y safepoints, con rotacion.
- `etapa-1-2500-mysql.csv`: muestras de los procesos MySQL.

La CPU del proceso se calcula entre la primera y la ultima muestra del
intervalo, a partir del tiempo de CPU acumulado. En esa metrica, 100 %
equivale a un nucleo logico. La CPU del sistema utiliza una escala de 0 a 100 %
para la capacidad total disponible. La memoria residente y la memoria heap
son mediciones diferentes.

Las mediciones indisponibles se registran como -1 en el CSV y como null en el
resumen. Los valores de conexiones son instantaneos y se leen por separado;
pueden no sumar exactamente entre si. Un promedio de cero en las muestras no
excluye esperas breves entre muestreos. Los contadores de GC son aproximados;
las pausas se contrastan con los logs de GC y safepoints. La instrumentacion
consume recursos y debe quedar identificada al comparar resultados.

Para interpretar el resumen, contrastar esperas persistentes por conexiones,
ocupacion de los hilos HTTP, CPU del sistema y de MySQL, memoria fisica libre,
heap y pausas. Las capturas de hilos ayudan a ubicar en que operaciones se
estaban esperando recursos. Estos datos orientan el siguiente cambio; no
constituyen por si solos una demostracion de causa.

La comprobacion local del nuevo modo utilizo MySQL 8.0.46, el JAR real y agentes
en ambas JVM. Se verificaron los MBeans de HikariCP y Tomcat, los logs de GC,
los resumenes de intervalos y la limpieza. Las pruebas del resumen comprueban
valores ausentes y filas incompletas. Los controles del proceso conservan los
seis casos de la correccion de Windows. El resultado de la ejecucion diagnostica
en el equipo Windows del proyecto se conserva en la seccion siguiente.

Referencias tecnicas:

- [Metricas JMX de HikariCP](https://github.com/brettwooldridge/HikariCP/wiki/MBean-%28JMX%29-Monitoring-and-Management).
- [Registro de MBeans de Tomcat](https://docs.spring.io/spring-boot/how-to/webserver.html).
- [Registro unificado de la JVM Java 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).

## Diagnostico observado en Windows: 18 de noviembre de 2026

La ejecucion `b6d6dd64786c` utilizo el comando anterior: 2500 usuarios,
5000 suministros preparados, 120 segundos de medicion, rampa de 60 segundos,
calentamiento de 15 segundos y 10 conexiones. La ventana medida fue
20:48:19 a 20:50:19 UTC, equivalente a 15:48:19 a 15:50:19 en Lima.
El generador termino con codigo 0 y sin fallos. Se informo continuidad de
sincronizacion y la retirada de la base y cuenta temporales.

Se atendieron 81 125 solicitudes sin errores registrados. Los p95 de las siete
operaciones estuvieron entre 4440 y 4531 ms; todas incumplieron el limite de
2000 ms. Se mantuvieron los 2500 usuarios requeridos para esta etapa.

| Metrica en las muestras del intervalo | Resultado |
|---|---:|
| CPU total del sistema, observada desde el backend | Promedio 99,686 %; minimo 97,476 % |
| CPU del proceso backend | 142,413 % de un nucleo logico |
| CPU de MySQL | Promedio 155,392 % de un nucleo logico |
| CPU del proceso generador | 32,992 % de un nucleo logico |
| Memoria fisica libre minima | 229,69 MiB |
| Heap usado maximo del backend | 152,09 MiB |
| Heap usado maximo del generador | 377 MiB |
| Conexiones activas | Promedio 2,474; maximo 10 |
| Hilos esperando conexion | Promedio 12,368; maximo 184 |
| Hilos HTTP ocupados | Promedio 42,474; maximo 200 |

El backend aporto 19 muestras; el generador y MySQL, 18 cada uno.
El generador tambien observo CPU total cercana al 100 %. La CPU del sistema
usa toda la capacidad como denominador; la CPU de cada proceso usa un nucleo.
Los intervalos y momentos de muestreo difieren, por lo que no se deben sumar
estos promedios para repartir exactamente el consumo total.

La evidencia confirma saturacion de CPU en este montaje compartido. La poca
memoria fisica libre requiere vigilancia; no se midio paginacion y no se
demuestra agotamiento del heap. Hubo picos de espera por conexiones y de
ocupacion HTTP, sin ocupacion permanente del pool en las muestras. Aumentar
el pool de 10 a 20 ya no aporto mejora apreciable en los ensayos anteriores.
No queda demostrado que la CPU sea la unica causa ni se excluyen mejoras de
consultas o del codigo. La comparacion directa de tiempos con las ejecuciones
anteriores esta limitada por la instrumentacion y el nuevo arranque de la JVM.

El resumen compartido, los parametros y los limites de interpretacion estan en
[evidencias/diagnostico-carga-windows-2026-11-18.json](evidencias/diagnostico-carga-windows-2026-11-18.json).
Se transcribio el JSON mostrado por el script; los CSV originales, las capturas
de hilos y los logs de GC permanecen en la carpeta local de esa ejecucion.

El siguiente ensayo debe elegir un entorno que permita distinguir el consumo
del generador y el del servidor. Se propone separar ambos en equipos distintos,
o usar un equipo de prueba con capacidad disponible comprobada. Si solo se
dispone de esta laptop, conservar este limite y revisar los logs originales
para buscar oportunidades de optimizacion antes de ampliar la carga. Cualquier
cambio debe medirse con la misma mezcla, datos y criterios. La herramienta
actual inicia todos los procesos localmente; separar equipos requiere adaptar
la preparacion y la direccion del servidor antes de ejecutar.

No se ejecuto la etapa de 5000 usuarios. EQ-01 y EQ-02 siguen pendientes;
el diagnostico no sustituye la medicion formal de 30 minutos ni la comparacion
de una configuracion inicial con otra que sostenga una carga mayor.
Como referencia de metodologia, la [guia oficial de pruebas grandes de Grafana
k6](https://grafana.com/docs/k6/latest/testing-guides/running-large-tests/)
explica la necesidad de vigilar los recursos del generador y del sistema bajo
prueba. El generador empleado aqui es la herramienta Java de este proyecto.
