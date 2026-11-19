# Cierre de validación del backend antes del frontend

19 de noviembre de 2026, sobre `890db94`.
Los trabajos P04, P05, P06 y P07 están comprobados en los casos y entornos
documentados. La [matriz vigente](matriz-requisitos-backend.md) identifica
como pendientes de interfaz P03 (vistas, gráficos y usabilidad) y P08
(dispositivos y recepción visual de avisos).

## Cambios y resultados

| Trabajo | Resultado |
|---|---|
| Transporte | Perfiles HTTPS para ambos servicios; TLS 1.2/1.3, confianza y nombre verificados, permisos y revocación. [Detalle](verificacion-https.md). |
| Contratos | Ocho puntos de integración, 78 casos adicionales de agua, identidad, firma y descifrado independiente de Web Push. [Matriz](contratos-externos.md). |
| Validación corregida | La empresa de agua podía entregar JSON con claves duplicadas y se conservaba el último valor. Ahora se rechaza el documento completo. Retirar la protección en una copia reproduce seis fallos; la versión corregida pasa los 78 casos. |
| Mantenibilidad | Cuatro adaptadores HTTP sustituidos por archivo; ocho respuestas completas equivalentes, valores esperados e idempotencia. 135 fuentes del núcleo sin cambios ni violaciones de los límites revisados. [Detalle](sustitucion-adaptadores.md). |
| Rendimiento y ampliación | 5000 usuarios/1800 s, ocho operaciones, 3,437,894 solicitudes, p95 1953–1998 ms y 0 errores en el perfil ampliado. [Resultados](escalabilidad-controlada.md). |
| Regresión | 760 pruebas del backend y 75 del proveedor aprobadas, sin fallos/errores/omitidas. Otros 130 casos seleccionados pasaron con MySQL 8.0.46. |

La evidencia es [reproducible y estructurada](evidencias/cierre-backend-2026-11-19.json).
Las pruebas de carga usan datos ficticios y no consultan cuotas de identidad
ni envían notificaciones a dispositivos reales. La recepción visual se
comprobará con la interfaz final.

## Aplicar el paquete en Windows

1. Detener con Ctrl+C las consolas que ejecuten los dos servicios.
2. Extraer el contenido del ZIP sobre `C:\Users\HP\Desktop\Nivora`,
   conservando la estructura de carpetas y aceptando reemplazar los archivos.
3. Verificar ambos módulos desde CMD:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\empresa-agua
mvnw.cmd clean verify
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

El resultado esperado es 75 y 760 pruebas respectivamente, ambos con
`BUILD SUCCESS`. Estas suites crean sus bases H2 y certificados temporales;
no necesitan los servicios locales encendidos. Esta entrega utiliza la
migración 22 ya aplicada y no añade una nueva migración SQL. El perfil HTTPS
es optativo; el desarrollo local habitual conserva su configuración.

Después de verificar:

```bat
cd /d C:\Users\HP\Desktop\Nivora
git status --short
git --no-pager diff --stat
git add .
git --no-pager diff --cached --stat
git commit -m "test(nivora-backend): cerrar validaciones previas al frontend"
git push
git status --short
```

La medición caracteriza el montaje descrito, no cualquier equipo. Al elegir
el despliegue final se verifican sus recursos, certificado y clientes.
El backend y sus contratos están preparados para comenzar la integración
del frontend con el alcance actual del proyecto.
