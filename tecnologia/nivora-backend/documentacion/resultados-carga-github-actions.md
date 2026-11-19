# Resultados de carga en GitHub Actions

Bloque 9, parte 3. Fecha local: 18 de noviembre de 2026 (America/Lima).
Los registros UTC corresponden al 19 de noviembre. Se revisaron los ZIP de
logs compartidos por el responsable del repositorio; no se repitieron las
pruebas ni se modificaron sus criterios para preparar este documento.

## Resultado y alcance

EQ-01 esta verificado en el montaje ficticio documentado, con pool 20:
5000 usuarios activos mantenidos durante 1800 segundos y sesiones independientes.
Las siete operaciones cumplieron p95 <= 2000 ms y registraron cero errores
internos, de transporte o de contenido. El generador registro cero fallos.
Durante la medicion se incorporaron 1124 lecturas y se generaron alertas;
el estado final tenia 1673 alertas acumuladas, no todas nuevas en esta etapa.

EQ-02 dispone de una comparacion observacional de configuraciones de pool:
se observa una carga mayor dentro de los umbrales con 20 conexiones. Los
hosts fueron diferentes. No se aisla el efecto del pool ni se demuestra una
ampliacion controlada de CPU/RAM o instancias. Ese alcance de escalabilidad
de infraestructura permanece pendiente; el ajuste del pool no se aplica
automaticamente a la configuracion habitual del backend.

## Procedencia y configuracion

- Repositorio: SebastianCH27/Nivora; commit `2d43391996ded72eb03d3743728ca78df0aa4ae2`.
- [Ejecucion completa, pool 10](https://github.com/SebastianCH27/Nivora/actions/runs/44445646813): montaje `638edfd57aa1`; ZIP `logs_120311975258.zip`.
- [Ejecucion completa, pool 20](https://github.com/SebastianCH27/Nivora/actions/runs/44452718181): montaje `d6e9e84eb9d7`; ZIP `logs_120330389144.zip`.
- Ambos procesos construyeron el backend con 559 pruebas, cero fallos, cero errores y cero omitidas.
- Huella SHA-256 del JAR, igual en ambos: `064237E55F59B0CC73AA0579612B2551E11351D5156294AF8EFBF78B771CD44E`.
- Linux x64, 4 procesadores logicos; RAM fisica registrada: 16 760 344 576 bytes con pool 10 y 16 464 306 176 bytes con pool 20.
- Temurin 21.0.13, MySQL 8.0.46-0ubuntu0.24.04.4, PowerShell 7.6.10.
- Heap maximo: backend y generador 512 MiB cada uno; fuente 128 MiB. Tomcat: 200 hilos; MySQL: max_connections=151, buffer InnoDB=128 MiB.
- Datos: 5000 suministros y sesiones, cuatro lecturas iniciales por suministro, tres periodos con consumo, un recibo y un aviso por suministro.
- Rampa de 60 s, calentamiento de 15 s y pausa de 1000 ms entre respuestas. Carga cerrada; cada usuario realiza una solicitud a la vez.
- Nivora, MySQL, fuente ficticia y generador comparten la VM. Recursos muestreados cada 5 s; las JVM de prueba se instrumentaron con el mismo agente.

Los 22 informes JSON permitidos de cada ejecucion, junto con las huellas
SHA-256 de los ZIP, estan preservados en
[la evidencia estructurada](evidencias/rendimiento-github-actions-2026-11-18.json).
Cada informe original conserva su contenido, incluidas las fechas y muestras.
Los ZIP originales son los registros de procedencia; no se guardan tokens,
CSV de usuarios ni contrasenas en este paquete.

## Etapas completas

| Pool | Usuarios mantenidos | Medicion | Solicitudes | p95 maximo entre operaciones | Errores | Sincronizacion y alertas | Cumple |
|---:|---:|---:|---:|---:|---:|---|---|
| 10 | 500 | 120 s | 59 840 | 6 ms | 0 | Si | Si |
| 10 | 1000 | 120 s | 119 229 | 25 ms | 0 | Si | Si |
| 10 | 2500 | 120 s | 169 611 | 1026 ms | 0 | Si | Si |
| 10 | 5000 | 1800 s | 2 457 146 | 2946 ms | 0 | Si | No |
| 20 | 500 | 120 s | 59 869 | 6 ms | 0 | Si | Si |
| 20 | 1000 | 120 s | 119 766 | 5 ms | 0 | Si | Si |
| 20 | 2500 | 120 s | 234 408 | 452 ms | 0 | Si | Si |
| 20 | 5000 | 1800 s | 3 375 041 | 1867 ms | 0 | Si | Si |

El minimo de usuarios activos coincide con el previsto en cada intervalo.
Los informes distinguen errores internos/transporte y otros errores: ambos
contadores fueron cero en todas las operaciones y etapas de ambos perfiles.
Cada etapa tiene evaluaciones de continuidad y muestras suficientes.
Los logs confirman la retirada de la base y cuenta ficticias al terminar.

## Las siete consultas con 5000 usuarios y pool 20

Ventana: 2026-11-19 03:50:01.758331006 a 04:20:01.758331006 UTC;
en Lima, 18 de noviembre de 22:50:01.758331006 a 23:20:01.758331006.

| Operacion | Solicitudes | p95 hasta | Errores | Cumple |
|---|---:|---:|---:|---|
| Consumo | 482 157 | 1820 ms | 0 | Si |
| Historial | 482 164 | 1823 ms | 0 | Si |
| Estadisticas | 482 166 | 1822 ms | 0 | Si |
| Costos | 482 129 | 1867 ms | 0 | Si |
| Proyeccion | 482 124 | 1861 ms | 0 | Si |
| Recibos | 482 143 | 1824 ms | 0 | Si |
| Notificaciones | 482 158 | 1793 ms | 0 | Si |

El maximo observado entre solicitudes individuales fue 2844,781 ms.
El requisito mide p95 por operacion: no exige que todas las respuestas
individuales sean inferiores a 2 segundos. El minimo de 100 muestras por
operacion se supero ampliamente. El ritmo agregado fue 1875,023 solicitudes/s.

## Recursos y comparacion

| Metrica de la ventana de 5000 usuarios | Pool 10 | Pool 20 |
|---|---:|---:|
| CPU total promedio | 99,471 % | 99,649 % |
| CPU backend, 100 % = un nucleo | 158,739 % | 164,189 % |
| CPU MySQL media, 100 % = un nucleo | 175,244 % | 175,732 % |
| CPU generador, 100 % = un nucleo | 56,000 % | 54,778 % |
| Memoria fisica libre minima | 12 612 624 384 bytes | 12 182 749 184 bytes |
| Conexiones activas medias | 9,772 de 10 | 19,769 de 20 |
| Hilos esperando conexion, media | 189,891 | 180,022 |
| Hilos HTTP ocupados, media | 200 de 200 | 200 de 200 |

Se observo una reduccion del p95 maximo de 2946 a 1867 ms y mas solicitudes
en la misma duracion. La evidencia no permite atribuir toda la diferencia
al pool: solo hay un perfil completo por configuracion, con VMs distintas,
sin control del modelo/frecuencia de CPU ni del rendimiento del host.

La CPU sigue casi totalmente ocupada y el margen del peor p95 es 133 ms.
El generador usa un heap maximo de 512 MiB y su maximo observado se acerca
a ese limite. No hubo fallos del generador, pero estos datos no prueban
holgura ni capacidad superior. Los contadores de GC no equivalen por si
solos a pausas totales de la aplicacion. No sumar promedios de procesos
para repartir exactamente la CPU: sus ventanas de muestreo difieren.

## Limites de la conclusion

El resultado se refiere al JAR, datos y mezcla descritos, con consultas
autenticadas sobre sesiones ya preparadas. No mide altas de sesion mediante
proveedores externos, navegadores reales, la red de usuarios finales ni
entrega Web Push. La validacion de carga comprueba HTTP 200, JSON y suministro;
la comprobacion funcional detallada y el aislamiento se verifican aparte.

Se preservaron todos los JSON emitidos por el flujo. Los CSV de muestras,
capturas de hilos y logs GC originales del montaje no estaban incluidos en
el ZIP de logs y desaparecieron con la VM; no se afirma conservarlos.
Este resultado verifica EQ-01 en el entorno documentado y deja una comparacion
util para EQ-02, con las reservas anteriores. No certifica por si solo la
capacidad de cualquier despliegue de produccion.
