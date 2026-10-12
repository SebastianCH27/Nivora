# Nivora — Boilerplate propio del laboratorio 03

Base académica del **Sistema para el monitoreo del consumo de agua potable**.
Está desarrollada específicamente para Nivora. Utiliza JavaScript, Node.js 24,
Express y una interfaz web adaptable; las reglas del núcleo son independientes
de estas tecnologías. No contiene funcionalidades del Marketplace.

El objetivo de esta base es ejecutar, leer y explicar la separación de capas,
los contratos y el intercambio de adaptadores. **No es una implementación
terminada de los 39 requisitos ni una solución de producción.**

## Incorporación en el repositorio

Extraer el ZIP y copiar su carpeta interna `nivora-boilerplate` directamente
en `C:\Users\HP\Desktop\Nivora`. La ruta debe quedar así:
`C:\Users\HP\Desktop\Nivora\nivora-boilerplate\package.json`.

El `package.json`, `src/app.js` y `src/server.js` de la raíz del repositorio
son los archivos iniciales del laboratorio 01. Esta base tiene su propio
paquete y sus propios archivos dentro de `nivora-boilerplate`.

Desde CMD:

```cmd
cd /d C:\Users\HP\Desktop\Nivora\nivora-boilerplate
npm ci
npm run pruebas
npm run verificar
npm start
```

Abrir **http://localhost:3000**. Mantener la terminal abierta. Para detener
el servidor, presionar `Ctrl+C`.

Se necesitan Node.js **24.17.0 o posterior dentro de la versión 24** y npm.
Las dependencias se descargan al ejecutar `npm ci`. Una vez instaladas,
la demostración principal funciona sin internet. Web Push utiliza servicios
externos y requiere conectividad.

## Datos para acceder

| Campo | Valor de prueba |
|-------|-----------------|
| Código de suministro | `13100390` |
| DNI | `00000001`, `00000002` o `00000003` |
| Suministro con consumo elevado | `13100391` |
| Suministro con lectura inicial faltante | `13100392` |

Todos los DNI, nombres, lecturas, tarifas y recibos son **ficticios**.
El código `13100390`, utilizado como referencia del proyecto, tampoco
consulta datos reales del hogar. La API de identidad de esta base es local
y simulada; no se integra con RENIEC ni acredita una identidad real.

El acceso de consulta tiene exactamente dos campos. Se acepta cualquier DNI
reconocido por la API ficticia con cualquier suministro existente: **no se
exige titularidad**. Otros DNI o códigos son rechazados. La aplicación
distingue datos inválidos de una API que no permite terminar la validación.
El registro indica el DNI utilizado y la identidad devuelta por esa consulta.

Para administrar, seleccionar **Acceso administrativo**:

| Campo | Valor local por defecto |
|-------|-------------------------|
| Usuario | `admin` |
| Contraseña | `NivoraDemo2026!` |

Estas credenciales públicas son únicamente para el entorno local de prueba.
Se pueden cambiar copiando `.env.example` a `.env` y configurando
`ADMIN_USUARIO` y `ADMIN_CLAVE`. Las sesiones administrativas y de consulta
tienen permisos distintos. Caducan a las ocho horas y se invalidan al salir.

## Qué demuestra

- Validación mediante dos API HTTP locales: empresa de agua e identidad.
- Sesiones mediante cookie HttpOnly y consultas limitadas al suministro
  asociado a la sesión. Una sesión de consulta no puede administrar.
- Consumo acumulado en litros y m³, cobertura y última lectura.
- Estimación de costo con tramos, conceptos y redondeo en céntimos.
- Proyección según el ritmo desde el inicio del mes hasta la última lectura.
- Historial, gráfico mensual, fechas y comparación con el mes anterior.
- Alerta cuando el mes actual supera el **total** del anterior, con datos
  suficientes y sin duplicar el aviso del mismo suministro y mes.
- Recibos recibidos de la empresa simulada; el importe del recibo se conserva
  como dato de esa fuente. No se genera a partir de la estimación del panel.
- Aviso de recibo disponible con plazo de una semana desde su disponibilidad.
- Avisos internos con lectura; asociación y desactivación de dispositivos.
- Actualización automática y recuperación después de una caída de la empresa.
- Supervisión administrativa de suministros, accesos, incidencias y entregas;
  activación de alertas y reintento de actualización.

La tarifa es ficticia. El estimado incluye un cargo fijo mensual completo.
Los importes del panel son estimaciones; los recibos de prueba contienen
importes definitivos dentro de la simulación. Nivora no procesa pagos.

## Las cuatro capas

| Capa y ubicación | Contenido | Dependencias |
|------------------|-----------|--------------|
| `src/dominio` | Validación, lecturas, periodos, costos, proyección y contratos | Solo archivos del dominio |
| `src/aplicacion` | Casos de acceso, panel, actualización, avisos y administración | Dominio y otros casos de aplicación |
| `src/infraestructura` | API HTTP, almacenamiento JSON, sesiones y Web Push | Contratos del dominio y herramientas externas |
| `src/presentacion` | API Express e interfaz web | Casos de uso y mecanismos de presentación |
| `src/app.js` | Raíz de composición | Selecciona y conecta implementaciones concretas |
| `src/server.js` | Arranque y tareas periódicas | Composición, servidor, simulador y almacenamiento |
| `simulacion` | Sistemas externos ficticios | Independiente de las reglas de cálculo de Nivora |

El navegador consulta la API propia de Nivora. Esta API utiliza los casos
de aplicación; las reglas no se recalculan en la pantalla. La integración
con la empresa obtiene las lecturas, tarifas y recibos por HTTP. La lectura
de consultas utiliza el repositorio de Nivora, incluso si la fuente falla.

Para explicar en clase:

1. Abrir `src/dominio/lecturas.js`: no importa Express ni acceso a archivos.
2. Abrir `src/dominio/contratos/puertos.js`: el núcleo define sus necesidades.
3. Abrir `src/aplicacion/actualizar-datos.js`: coordina validación, deduplicación
   y avisos a través de contratos.
4. Abrir `src/infraestructura/fuentes-http.js`: adapta las API a esos contratos.
5. Abrir `src/app.js`: aquí se eligen las implementaciones.
6. Ejecutar `npm run pruebas` y `npm run verificar`.

`verificar` revisa las importaciones estáticas del núcleo. Las pruebas de
dominio pueden ejecutarse solas con `node --test pruebas/dominio.test.js`;
no necesitan navegador, Express ni servidor. Las pruebas de integración
sí arrancan servidores locales en puertos temporales.

## Simulación y almacenamiento

La API propia usa el puerto `3000`; el simulador externo usa `3001`, escucha
solo en `127.0.0.1` y lleva un token interno generado al arrancar. Ambos se
inician con `npm start`; no es necesario ejecutar dos comandos distintos.

La fecha inicial de los **datos** es el 11 de octubre de 2026 a las 12:00 en
Lima. Cada ciclo añade una lectura al suministro `13100390` y avanza un
minuto el reloj de la fuente. Los registros de acceso y recepción utilizan
la fecha real del servidor. Este reloj acelerado permite repetir ejemplos;
no representa mediciones reales en tiempo real. Los periodos se delimitan
en la zona horaria de Lima y la demostración permanece en octubre de 2026.

Los datos se almacenan en `datos/nivora.json`, excluido de Git. Se escriben
mediante archivo temporal y renombrado, con un retraso de hasta 250 ms tras
modificaciones. Las sesiones viven en memoria y requieren nuevo acceso
después de reiniciar. Para restaurar todos los datos ficticios iniciales,
detener Nivora y eliminar únicamente su carpeta local `datos`.

El adaptador JSON permite continuar las consultas durante una caída de la
API y conservar la demostración al reiniciar. No admite varias instancias,
no ofrece transacciones de base de datos y no garantiza durabilidad ante
un corte abrupto. Antes de escalar deberán sustituirse este adaptador y las
sesiones por almacenamiento compartido apropiado.

Desde administración pueden aplicarse estos escenarios:

| Escenario | Resultado esperado |
|-----------|--------------------|
| Consumo elevado | Incorpora 8000 litros adicionales al suministro `13100390` y evalúa la alerta |
| Lectura duplicada | No incrementa el consumo ni crea otra lectura almacenada |
| Lectura inválida | Registra una incidencia y excluye el dato de los cálculos |
| Publicar recibo de septiembre | El simulador incorpora el recibo y Nivora registra el aviso y su fecha límite |
| Interrumpir / restaurar API de agua | Conserva consultas existentes; bloquea nuevas validaciones que necesiten esa API; recupera pendientes al restaurar |
| Interrumpir / restaurar identidad | El acceso informa que no puede validar; las sesiones ya iniciadas siguen consultando |

Las nuevas lecturas siguen generándose mientras la fuente de agua está
interrumpida. Se recuperan al restaurarla. El suministro `13100392` muestra
el caso de información incompleta. El recibo de septiembre se publica
durante octubre: este escenario representa disponibilidad posterior al
cierre de ese mes; no emite un recibo definitivo del mes aún en curso.

## Notificaciones al dispositivo: Web Push

El proyecto incluye un adaptador de envío con `web-push`, suscripción del
navegador y un service worker que muestra avisos sin depender del panel
abierto. Los nuevos avisos se procesan periódicamente en el servidor;
los errores quedan pendientes para reintento. Los avisos internos se
conservan aunque no se configure o falle Web Push.

Para habilitarlo:

```cmd
copy .env.example .env
npm run vapid
```

Copiar las claves generadas en `.env`, establecer un correo de contacto en
`VAPID_SUBJECT` y reiniciar Nivora. Generarlas una sola vez y conservarlas;
no subir `.env` ni claves privadas al repositorio. Luego entrar como usuario,
abrir **Avisos** y pulsar **Activar en este dispositivo**, concediendo permiso.

Para probar la entrega con Nivora cerrada:

1. Asociar el navegador del usuario a `13100390`.
2. Cerrar la pestaña o aplicación de ese usuario y mantener el servidor activo.
3. En otro navegador o perfil entrar como administrador.
4. Aplicar **Consumo elevado** o **Publicar recibo de septiembre**.
5. Comprobar el aviso en el dispositivo y registrar navegador, permiso,
   conectividad y resultado. Si el evento ya existía, reiniciar la simulación
   con datos iniciales antes de repetir; los avisos no se duplican.

Este paquete no incluye evidencia de recepción en un dispositivo físico.
Una entrega marcada `aceptada` significa aceptación por el proveedor;
no confirma que apareció en la pantalla. La compatibilidad depende del
navegador y sus condiciones de funcionamiento en segundo plano. El
adaptador admite endpoints de FCM, Mozilla y Apple; otros proveedores
requieren revisar su integración.

Los permisos y suscripciones requieren un contexto seguro: `localhost` para
pruebas en la misma computadora, o HTTPS. Entrar desde un celular a una IP
de red local por HTTP permite consultar cuando se configura el servidor
para esa red, pero no habilita automáticamente Web Push. En esa situación,
`localhost` en el teléfono representa al teléfono, no a la computadora.

Referencias de implementación:

- [Push API — MDN](https://developer.mozilla.org/en-US/docs/Web/API/Push_API)
- [web-push — repositorio oficial](https://github.com/web-push-libs/web-push)

## Prueba de carga

Con Nivora ejecutándose en una terminal, abrir otra en esta carpeta:

```cmd
npm run carga -- --usuarios=50 --segundos=12 --rampa=2 --pausa-min=500 --pausa-max=1000
```

Este es un escenario breve para comprobar el generador. Para preparar la
evaluación del requisito de 5000 usuarios durante 30 minutos:

```cmd
npm run carga -- --usuarios=5000 --segundos=1800 --rampa=300
```

El generador crea cookies independientes y mantiene consultas de consumo,
costos, proyección, historial, recibos y avisos con pausas de 3 a 10 segundos.
La actualización y evaluación de alertas del servidor siguen ejecutándose.
Los resultados se guardan en `resultados/carga-*.json`, excluidos de Git.
Se miden tiempos desde el generador, incluyendo red y API. En un informe
formal se deben distinguir estos tiempos de las medidas internas de la API.

Los registros incluyen usuarios que accedieron, mínimo de usuarios durante
la fase sostenida, p95 y errores por operación. Las comprobaciones de
contenido son básicas; no sustituyen las pruebas de exactitud y permisos.
Documentar aparte infraestructura del servidor, consumo de CPU y memoria,
volumen de datos, actividad de actualización y evidencia de alertas.

**No se ha validado el objetivo de 5000 usuarios durante 30 minutos.**
Este adaptador de una instancia es una base para estudiar y medir. El
requisito de capacidad del proyecto se conserva; deberá verificarse con
la implementación y el almacenamiento adecuados.

## Alcance y siguientes etapas

Consultar `docs/alcance.md` para relacionar lo demostrado con los requisitos.
Quedan pendientes la API de identidad real, la integración real con la
empresa, infraestructura HTTPS, edición administrativa de periodos y
tarifas, cambios tarifarios dentro de un mismo mes y la evaluación completa
de capacidad. La tarifa del ejemplo cubre cada periodo completo; si no hay
una versión compatible se informa que el costo no está disponible.

La sustitución de adaptadores requiere conservar y verificar sus contratos;
no significa que una API real pueda conectarse sin adaptar formatos,
credenciales, errores y semántica de los datos.

No se implementan procesamiento de pagos, electricidad, recomendaciones,
metas personales ni reportes descargables, conforme a los límites de Nivora.

## Registro en Git

Después de ejecutarlo y verificarlo, detener el servidor y regresar a la
raíz del repositorio:

```cmd
cd ..
git status --short
git add nivora-boilerplate
git --no-pager diff --cached --stat
git commit -m "feat(lab03): incorporar boilerplate propio de Nivora"
git push origin main
git status
```

La carpeta contiene su propio `.gitignore`: excluye dependencias instaladas,
datos de ejecución, resultados y `.env`. El ZIP no contiene una carpeta `.git`.

En `analisis-de-sistema/03-requisitos-funcionales.md`, separar RF02 y RF03
si aparecen concatenados como `... esa situación. || RF03 | ...`. Cada
requisito debe quedar en una línea distinta de la tabla Markdown.
