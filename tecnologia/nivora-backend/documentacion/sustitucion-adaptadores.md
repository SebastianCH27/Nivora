# Sustitución controlada de la fuente de agua

P07/EQ-10, comprobado el 19 de noviembre de 2026 sobre la base `890db94`.

`SustitucionAdaptadoresTests` ejecuta dos aplicaciones Nivora aisladas con
el mismo reloj y el mismo conjunto ficticio de suministros, lecturas,
tarifas y recibos. La primera usa los adaptadores HTTP habituales. La segunda
inyecta cuatro implementaciones desde archivo mediante los contratos
`FuenteSuministros`, `FuenteLecturasConCorte`, `FuenteTarifas` y `FuenteRecibos`.
El adaptador de archivo es código exclusivo de pruebas.

La sustitución solo requiere composición e infraestructura de pruebas.
No cambia ninguna fuente de dominio o aplicación. La fuente alternativa
lee el archivo local directamente; no reutiliza el cliente HTTP ni los
adaptadores HTTP sustituidos. Sí utiliza los mismos objetos de dominio
y sus validaciones.

Se comparan por igualdad los cuerpos completos de consumo, historial,
estadísticas, costos, proyección, recibos, notificaciones y comparación.
Además se comprueban valores esperados independientes: consumo actual
2000 L, anterior 1000 L, estimación actual S/10.00, recibo S/7.50 y proyección
6000 L / S/20.00. La alerta y el aviso se generan una sola vez.
La cuenta de peticiones demuestra que, con el archivo, únicamente identidad
conserva una llamada HTTP; las cuatro fuentes de agua no la utilizan.

`DependenciasArquitecturaTests` revisa todas las fuentes de dominio y
aplicación e impide referencias a infraestructura, presentación, Spring,
JDBC, clientes de red, archivos y Jackson. La revisión incluye referencias
de tipos escritas con su nombre completo. Se permiten tipos de valor como
`URI` y utilidades criptográficas de propósito general utilizadas para
validar una suscripción; no realizan comunicaciones con proveedores.

Esta comprobación protege los límites de dependencias examinados. No pretende
probar cualquier forma imaginable de acceso indirecto, como reflexión dinámica.
La regresión completa comprueba el comportamiento de los servicios después
de ampliar las verificaciones.

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd -Dtest=SustitucionAdaptadoresTests,DependenciasArquitecturaTests test
```
