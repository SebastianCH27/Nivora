# Validación de la entrega

Fecha de comprobación: **11 de octubre de 2026, hora de Lima**.

| Comprobación | Resultado observado |
|--------------|---------------------|
| Instalación de dependencias | Completada con el archivo de bloqueo incluido |
| `npm run pruebas` | 26 pruebas aprobadas: dominio, API, permisos, integración y reintento de avisos |
| `npm run verificar` | 11 archivos del núcleo cumplen las restricciones de importaciones estáticas |
| Interfaz en Chromium | Acceso, resumen, historial, recibos, avisos, salida y acceso administrativo funcionando sin errores de JavaScript observados |
| Vista de escritorio | Revisada a 1440 × 1000 píxeles |
| Vista de celular | Revisada a 390 × 844 píxeles, sin desbordamiento horizontal del documento |
| Generador de carga reducido | 50 sesiones, rampa de 2 segundos y fase sostenida de 12 segundos; sin fallos de solicitud ni de las comprobaciones básicas de respuesta |
| `npm audit --omit=dev` | Cero vulnerabilidades reportadas durante la consulta de esta entrega |

La ejecución se comprobó en Linux con **Node.js 24.19.0**. El paquete declara
compatibilidad con Node.js 24 desde **24.17.0**, que es la versión del entorno
del estudiante. Corresponde repetir la instalación y las pruebas en su
computadora Windows antes de registrar la incorporación.

No se comprobaron todavía la recepción Web Push en dispositivos físicos,
el perfil de 5000 usuarios durante 30 minutos, un despliegue HTTPS ni las
integraciones reales. Las pruebas automatizadas de envío utilizan un emisor
ficticio y comprueban conservación y reintento; no acreditan recepción externa.

El informe de carga reducido es una comprobación inicial del generador,
con actualización automática activa. No prueba escalabilidad ni sustituye
la evidencia de carga y recursos solicitada por los atributos de calidad.
