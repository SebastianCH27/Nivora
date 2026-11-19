# Pruebas de carga en GitHub Actions

Bloque 9, parte 3. Este procedimiento ejecuta el montaje de datos ficticios en
una maquina temporal de GitHub. La laptop se utiliza para iniciar la ejecucion
y consultar sus resultados. La meta de 5000 usuarios durante 30 minutos sigue
pendiente hasta obtener la evidencia correspondiente.

## Condicion de gratuidad

El trabajo esta condicionado a que el repositorio sea publico. Utiliza el runner
estandar `ubuntu-24.04`, cuyo computo esta cubierto gratuitamente para esos
repositorios. En un repositorio privado el trabajo se omite antes de asignar
una maquina: un trabajo omitido no equivale a una prueba aprobada.

GitHub documenta 4 vCPU y 16 GB de RAM para ese runner publico. La capacidad
efectiva se debe medir: el backend, MySQL, la fuente y el generador siguen
compartiendo la VM. No se asume que esos recursos garanticen 5000 usuarios.

El flujo no guarda caches ni sube artefactos. Publica un conjunto permitido de
JSON en los logs y una tabla en el resumen de la ejecucion. Los logs y el
resumen no cuentan como almacenamiento de artefactos, segun GitHub. No emplea
runners ampliados, suscripciones de Azure, tokens de APIsPERU, claves VAPID ni
contrasenas de la laptop.

## Archivos y puesta en marcha

Copiar las carpetas `.github`, `docs` y `tecnologia` del paquete sobre la raiz
del repositorio Nivora. La ruta del flujo debe quedar exactamente asi:

```text
Nivora/.github/workflows/nivora-carga.yml
```

Revisar los cambios y publicarlos en la rama predeterminada del repositorio
mediante el procedimiento habitual de Git. El flujo solo tiene el disparador
manual `workflow_dispatch`: hacer push lo registra pero no inicia la carga.
La cuenta debe disponer de permiso de escritura y Actions debe estar habilitado
en el repositorio. No es necesario recompilar ni arrancar los servicios locales.

En GitHub:

1. Abrir el repositorio y seleccionar **Actions**.
2. Seleccionar **Nivora - pruebas de carga**.
3. Pulsar **Run workflow**, elegir `main` y el perfil `breve`.
4. Mantener `pool_conexiones` en `10` para la primera ejecucion.
5. Pulsar **Run workflow** y abrir el trabajo `carga` para ver su progreso.

Si el flujo no aparece, comprobar que `.github/workflows/nivora-carga.yml` este
en la rama predeterminada. Si el trabajo aparece omitido, comprobar la
visibilidad del repositorio. El flujo no cambia esa visibilidad.

## Perfiles

| Perfil | Usuarios medidos | Rampa y calentamiento | Medicion |
|---|---:|---|---|
| `breve` | 20 | 5 s y 5 s | 45 s |
| `diagnostico-2500` | 2500 | 60 s y 15 s | 120 s |
| `completa` | 500, 1000, 2500 y 5000 | 60 s y 15 s por etapa | 120 s en las primeras tres; 1800 s en la ultima |

Todos preparan 5000 suministros ficticios y sesiones independientes. Se mantiene
la mezcla de siete consultas, la pausa de 1000 ms y los criterios definidos en
[Rendimiento y carga](verificacion-rendimiento.md). El perfil completo se detiene
si una etapa incumple los umbrales. Seleccionar `completa` no acredita que las
cuatro etapas hayan sido ejecutadas.

La prueba breve comprueba el montaje antes de dedicar tiempo al perfil completo.
Tras aprobarla, ejecutar `completa` con el mismo pool. Si una etapa falla,
conservar sus resultados y utilizar el diagnostico para investigar el limite.
La opcion de pool `20` permite una comparacion; aumentar el pool no garantiza
una mejora. EQ-02 exige demostrar que una ampliacion de recursos sostiene una
carga mayor, manteniendo la mezcla y los criterios.

## Preparacion automatica y recursos

El flujo construye el JAR con Java 21 y `mvnw clean verify`. Inicia MySQL 8 de
la imagen del runner; la credencial `root` usada por ese entorno es publica y
pertenece exclusivamente a la VM temporal. No se debe sustituir por claves de
la base local. El script crea su base `nivora_carga_<id>` y su cuenta ficticia,
y las retira en su bloque de limpieza.

`EjecutarCargaGitHub.ps1` invoca el motor con `-SinInteraccion`. Ese modo exige
`NIVORA_CARGA_DB_ADMIN_CLAVE` en el entorno y falla si falta; el modo habitual
de CMD sigue solicitando la clave de forma oculta. El usuario no necesita
configurar secretos del repositorio para este montaje ficticio.

Los perfiles breve y completo usan `-MedirRecursos`. El agente local registra
CPU del sistema, CPU y heap de las JVM, HikariCP, hilos HTTP y GC cada cinco
segundos; tambien se muestrea MySQL. Cada etapa conserva su resumen y una copia
del CSV del generador antes de comenzar la siguiente JVM. El CSV del backend
abarca toda la ejecucion y los resumenes filtran su ventana estable exacta.
Los logs GC del generador pueden rotarse al iniciar etapas posteriores.

`-Diagnostico` conserva su restriccion a una sola etapa y anade hasta tres
capturas de hilos. `-MedirRecursos` permite varias etapas sin esas capturas.
Ambos modos incluyen instrumentacion; su coste debe quedar identificado al
comparar resultados con pruebas anteriores sin agente.

## Leer y conservar el resultado

En el resumen de la ejecucion aparece una tabla con consultas, p95 maximo de
las operaciones, sincronizacion y alertas. En el paso **Ejecutar carga y
publicar resumen**, abrir los grupos **Informe ...** para ver los JSON completos:

- `entorno.json` y `mysql.json`: configuracion y versiones observadas.
- `etapa-...json`: usuarios, duracion, errores y latencias por operacion.
- `etapa-...-evaluacion.json`: actividad de sincronizacion y alertas.
- `etapa-...-diagnostico.json`: recursos de la ventana medida.
- `etapa-...-antes.json` y `etapa-...-despues.json`: estado de los datos ficticios.

El paso falla si hay errores de montaje, si no existen evaluaciones, si faltan
muestras de recursos, si falla el control de latencia y errores, o si no se
observan sincronizacion y generacion de alertas durante la medicion. Para
`completa` exige ademas cuatro evaluaciones y cobertura de 5000 usuarios durante
1800 segundos. Un resultado verde del perfil breve solo acredita ese perfil.

Los logs pueden descargarse desde la ejecucion con **Download log archive**.
Conservarlos fuera de GitHub para adjuntar la evidencia del requisito.
No se imprime ni publica `usuarios.csv`, el JAR, claves, dumps SQL ni los logs
completos de la aplicacion. La VM temporal y sus archivos desaparecen al terminar.
Las capturas de hilos y CSV originales no se conservan como artefactos; si se
necesitan para investigar un fallo remoto, se debe preparar su exportacion
selectiva antes de repetir la ejecucion.

## Validacion de esta entrega

La validacion local comprueba sintaxis YAML y PowerShell, el modo automatico
contra MySQL aislado, el resumen y las metricas en varias etapas. Se comprueba
tambien que los fallos de los criterios no se publiquen como aprobados.
Se midieron tres etapas locales de 20 usuarios durante 45 segundos cada una,
con 5000 suministros preparados: 2685 consultas, cero errores y p95 maximo de
11 ms. Continuaron la sincronizacion y las alertas, y se conservaron las
metricas del generador de cada etapa. Diez escenarios comprobaron los controles
del resumen y la exclusion de archivos no permitidos. Se comprobo tambien que
el modo automatico rechace una clave administrativa ausente.
La evidencia esta en
[validacion-automatizacion-carga-2026-11-18.json](evidencias/validacion-automatizacion-carga-2026-11-18.json).
Estas comprobaciones no son una ejecucion en GitHub: el primer resultado del
runner remoto debe obtenerse mediante el perfil breve descrito arriba.

## Referencias

- [Recursos de los runners estandar](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
- [Computo gratuito y almacenamiento de Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions).
- [Ejecucion manual y rama predeterminada](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
- [Software de la imagen Ubuntu 24.04](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md).
