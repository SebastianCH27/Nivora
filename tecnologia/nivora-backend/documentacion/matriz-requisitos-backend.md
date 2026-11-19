# Matriz de revisión de requisitos del backend

Bloque 9, parte 5. Revisión original del 19 de noviembre de 2026 (America/Lima),
con el contenido de `6cc8260` informado por el responsable del repositorio.
La fuente de alcance es [RF01–RF39](../../../docs/01-analisis-de-sistema/03-requisitos-funcionales.md)
y los [escenarios EQ-01–EQ-10](../../../docs/01-analisis-de-sistema/08-escenarios-atributos-calidad.md).

**Backend comprobado** significa que hay implementación y pruebas del
comportamiento indicado en la API o sus procesos. **Parcial** identifica
una parte del requisito o de su evaluación que sigue abierta. **Pendiente**
indica que falta ejecutar la evaluación específica. Estos estados no
acreditan la experiencia completa del producto: la interfaz final todavía
debe implementarse y comprobarse, incluso para las filas verificadas en API.

Las evidencias E01–E18 enlazan documentos y clases reales. Los métodos
concretos y el inventario de la regresión de aquel corte se conservan en
[el resumen estructurado](evidencias/revision-final-backend-2026-11-19.json).
Los identificadores P01–P08 conservan los trabajos detectados y su estado,
sin modificar los requisitos originales.

## Actualización posterior a cc65d63

La entrega de [lecturas de intervalo](lecturas-intervalo.md), del 19 de
noviembre de 2026, completa RF10/P01 y amplía los casos de EQ-03. Su
[evidencia propia](evidencias/lecturas-intervalo-2026-11-19.json) contiene
la regresión de 602 pruebas. El resumen de revisión final enlazado arriba
conserva el corte histórico de 562 pruebas del bloque 9.

La [comparación de consumo y costos](comparacion-costos.md), ampliación
sobre `cf2f699`, completa RF20/P02 con 632 pruebas de regresión aprobadas.
Su [evidencia](evidencias/comparacion-costos-2026-11-19.json) conserva los
casos de pares mensuales, coberturas, conceptos ausentes y aislamiento.

## Corrección de incorporación automática de tarifas

La revisión integral posterior a `457b96c` detectó que el importador tarifario
solo se ejecutaba manualmente. La [corrección de sincronización](sincronizacion-tarifas.md)
conecta la incorporación inicial y el refresco de los suministros registrados,
con calendario persistido, reservas y reintentos. La regresión de aquella corrección tiene
656 pruebas aprobadas. E21 identifica el recorrido sin preparación manual,
la conservación durante fallos y la prioridad de publicaciones administrativas.
Los registros de 562, 602 y 632 pruebas conservan su alcance histórico.

## Cierre de validación previo al frontend

Sobre `890db94`, se completaron las evaluaciones P04, P05, P06 y P07.
La regresión vigente es de **760 pruebas del backend y 75 de la Empresa
de Agua**, sin fallos, errores ni omitidas. Otras 130 comprobaciones
seleccionadas pasaron con MySQL 8.0.46; son un recorrido adicional y no
130 casos nuevos de la suite. La [evidencia del cierre](evidencias/cierre-backend-2026-11-19.json)
conserva el inventario, los resultados de carga y la equivalencia del JAR.
Las tareas P03 y P08 se realizarán al implementar las interfaces y validar
su uso y notificaciones en los dispositivos elegidos.

## Requisitos funcionales

| ID | Función | Estado en esta revisión | Alcance comprobado y reserva | Evidencia |
|---|---|---|---|---|
| RF01 | Entrada de suministro y DNI | Backend comprobado | Valida formato, tipos y presencia antes de invocar las fuentes. | [E01](#e01) |
| RF02 | Comprobación externa de suministro e identidad | Backend comprobado | Distingue inexistencia confirmada, credencial/cuota, respuesta incompatible y caída; no crea sesión sin validación. | [E01](#e01), [E17](#e17) |
| RF03 | Sesión asociada al suministro | Backend comprobado | Inicio, consulta, cierre, caducidad y aislamiento entre suministros. | [E01](#e01), [E11](#e11) |
| RF04 | Registro del acceso | Backend comprobado | Conserva suministro, identidad y fecha; restringe la consulta administrativa y enmascara el DNI. | [E01](#e01), [E14](#e14) |
| RF05 | Recuperación automática de lecturas | Backend comprobado | Programación persistida, lotes acotados y reintentos separados de las consultas. | [E03](#e03) |
| RF06 | Registro de lecturas | Backend comprobado | Conserva fuente, identificador, medidor, recepción y fecha final; los intervalos también conservan su inicio. Ambos tipos usan litros enteros. | [E02](#e02), [E19](#e19) |
| RF07 | Validación de lecturas e incidencias | Backend comprobado | Valida ambos extremos, recepción, inventario y superposiciones; conserva los registros aceptados ante conflictos. | [E02](#e02), [E17](#e17), [E19](#e19) |
| RF08 | Detección de duplicados | Backend comprobado | Repetición, identidad de la medición y concurrencia no agregan una segunda copia. | [E02](#e02) |
| RF09 | Recuperación tras fallos | Backend comprobado | Conserva progreso y lecturas aceptadas y reanuda el recorrido sin doble conteo. | [E03](#e03), [E14](#e14) |
| RF10 | Cálculo según tipo de medición | Backend comprobado | Diferencias de acumuladas y suma de intervalos no superpuestos. Identifica huecos, cruces de periodo, mezcla y desbordamiento, sin prorratear. P01 completado en los casos definidos. | [E04](#e04), [E19](#e19) |
| RF11 | Litros y metros cúbicos | Backend comprobado | La API entrega ambas magnitudes con conversión decimal explícita. La selección y presentación visual corresponden al frontend. | [E04](#e04) |
| RF12 | Acumulado mensual y fechas | Backend comprobado | Usa los límites del calendario configurado y las fechas efectivamente medidas. | [E04](#e04), [E12](#e12) |
| RF13 | Cobertura e incompatibilidades | Backend comprobado | Distingue SIN_DATOS, PARCIAL, COMPLETO e INCOMPATIBLE; un dato ausente no se convierte en cero. | [E04](#e04) |
| RF14 | Última medición y actualización | Backend comprobado | Publica fecha de medición y estado aun cuando falle la fuente o empiece un mes sin lecturas. | [E04](#e04), [E14](#e14) |
| RF15 | Costo acumulado estimado | Backend comprobado | Incorpora y refresca tarifas automáticamente; aplica la vigencia y versión tarifaria al consumo calculable del periodo. | [E06](#e06), [E21](#e21) |
| RF16 | Detalle del costo estimado | Backend comprobado | Entrega consumo, selección tarifaria y conceptos; indica conceptos faltantes. | [E06](#e06), [E21](#e21) |
| RF17 | Proyección de consumo y costo | Backend comprobado | Proyecta con el ritmo observado, duración mensual y tramos de la tarifa aplicable. | [E06](#e06), [E21](#e21), [E07](#e07) |
| RF18 | Fecha, supuestos e insuficiencia | Backend comprobado | Expone fecha y supuesto de ritmo constante; diferencia insuficiencia de lecturas, tarifa y conceptos. | [E07](#e07) |
| RF19 | Consumo y costos anteriores | Backend comprobado | Devuelve periodos anteriores con cobertura, consumo y estimación versionada. | [E04](#e04), [E06](#e06), [E21](#e21) |
| RF20 | Comparación entre periodos | Backend comprobado | Compara dos meses elegidos o el anterior y el actual; devuelve fechas, duraciones, diferencias de litros, m³ y costo estimado, cobertura y tarifas. Distingue datos parciales y totales ausentes. P02 completado en los casos definidos. | [E05](#e05), [E06](#e06), [E20](#e20), [E21](#e21) |
| RF21 | Gráficos y estadísticas | Parcial | Existe serie mensual con huecos y resumen basado en meses completos. P03: falta la interfaz que represente los gráficos y su validación visual. | [E05](#e05) |
| RF22 | Alerta por superar el mes anterior | Backend comprobado | Exige cobertura suficiente; contempla consumo anterior cero y conserva la primera detección. | [E09](#e09) |
| RF23 | Preferencias por dispositivo | Backend comprobado | Asocia UUID y suministro de la sesión y exige permiso para habilitar los avisos. | [E09](#e09), [E10](#e10) |
| RF24 | Persistencia y unicidad de avisos | Backend comprobado | Conserva evento, periodo y fecha; evita avisos duplicados y mantiene la bandeja ante fallos del emisor. | [E08](#e08), [E09](#e09), [E10](#e10) |
| RF25 | Bandeja y marcas de lectura | Backend comprobado | Consulta paginada por suministro; marca leída idempotente entre sesiones. | [E09](#e09), [E11](#e11) |
| RF26 | Entrega con la aplicación cerrada | Parcial | Cola, suscripción y service worker Web Push implementados; recepción observada manualmente en Windows/Chrome. P08: falta documentar la matriz de dispositivos/navegadores admitidos y su recepción visual con la interfaz final. | [E10](#e10) |
| RF27 | Importación de recibos emitidos | Backend comprobado | Sincroniza importe definitivo, disponibilidad y periodo desde la fuente simulada; conserva los datos originales. | [E08](#e08) |
| RF28 | Consulta de recibos y vencimientos | Backend comprobado | API autenticada y paginada; distingue la ventana facturada del periodo de análisis configurado. | [E08](#e08), [E12](#e12) |
| RF29 | Aviso de recibo disponible | Backend comprobado | Registra un aviso por recibo y comunica periodo y vencimiento; demostración con siete días desde la disponibilidad. La recepción final en cada dispositivo conserva la reserva de RF26. | [E08](#e08), [E10](#e10) |
| RF30 | Acceso y permisos administrativos | Backend comprobado | Sesiones diferenciadas, control de permisos, cambio inicial de clave y revocación. | [E01](#e01), [E11](#e11) |
| RF31 | Suministros y registros de acceso | Backend comprobado | Lista local, paginación, protección del DNI y permisos. | [E14](#e14) |
| RF32 | Configuración de periodos mensuales | Backend comprobado | Reglas futuras versionadas, control de revisión y aplicación a consultas, alertas y contexto de recibos. | [E12](#e12), [E14](#e14) |
| RF33 | Gestión tarifaria versionada | Backend comprobado | Publica vigencias y tramos; conserva versiones y resultados de periodos anteriores. | [E06](#e06), [E13](#e13) |
| RF34 | Control de evaluación de alertas | Backend comprobado | Pausa persistida y reanudación autorizadas sin cambiar la regla de comparación. | [E09](#e09) |
| RF35 | Supervisión de integración | Backend comprobado | Lecturas y recibos exponen estado, último intento/éxito y reservas por suministro. | [E14](#e14) |
| RF36 | Incidencias de integración | Backend comprobado | Conserva origen, motivo, fechas y estado; pagina sin mezclar suministros. | [E02](#e02), [E14](#e14) |
| RF37 | Reintentos administrativos | Backend comprobado | HTTP 202 programa; respeta bloqueo y reserva; recuperación posterior conserva validaciones y duplicados. | [E14](#e14), [E15](#e15) |
| RF38 | Fuente simulada de agua | Backend comprobado | El módulo empresa-agua proporciona suministros, lecturas, tarifas y recibos; los adaptadores y recorridos se prueban con HTTP controlado. La carga usa además su propia fuente ficticia acotada, descrita en la evidencia. | [E08](#e08), [E13](#e13), [E16](#e16), [E17](#e17) |
| RF39 | Escenarios de prueba | Backend comprobado | Fuentes, relojes y datos de prueba representan accesos válidos/erróneos, consumo elevado, huecos, duplicados, recibos y caídas. La evaluación de tareas desde la interfaz corresponde a P03. | [E01](#e01), [E02](#e02), [E04](#e04), [E08](#e08), [E09](#e09), [E10](#e10) |

## Escenarios de calidad

| ID | Estado | Evidencia y alcance | Reserva |
|---|---|---|---|
| EQ-01 | Verificado en entorno | 5000 sesiones durante 1800 s; ocho operaciones, p95 1953–1998 ms y 0 errores; lecturas, alertas y tarifas activas. [E25](#e25). | Montaje ficticio y carga cerrada documentados; evaluar los recursos del despliegue elegido. |
| EQ-02 | Verificado en entorno | Mismo JAR y datos, CPU servidor/BD de 1 a 4 núcleos, generador separado; la ampliación sostiene una carga mayor y 5000 usuarios durante 30 minutos. [E25](#e25). | P05 completado en el experimento controlado; capacidad inicial exacta por debajo de 500 no medida. |
| EQ-03 | Verificado en casos | Acumuladas e intervalos, validación, unicidad, cobertura, costos y proyecciones comprobados. [E19](#e19). | Alcance: contratos, datos y casos definidos; incluye periodos incompatibles o incompletos. |
| EQ-04 | Verificado en casos | Las consultas locales y avisos se conservan durante caídas y los reintentos recuperan pendientes. [E03](#e03), [E08](#e08), [E10](#e10), [E14](#e14), [E21](#e21). | Casos definidos con fuentes y emisor controlados; no es una certificación general de disponibilidad. |
| EQ-05 | Verificado en casos | Fechas, referencias de lecturas, vigencias, revisiones y supuestos comprobados. [E04](#e04), [E06](#e06), [E07](#e07), [E12](#e12). | Alcance de las lecturas y consultas implementadas. |
| EQ-06 | Verificado en entorno | Sesiones, permisos, revocación y despliegue HTTPS con TLS 1.2/1.3 y certificados verificados. [E01](#e01), [E11](#e11), [E14](#e14), [E22](#e22). | P04 completado en el montaje de pruebas; configurar y comprobar el certificado del despliegue final. |
| EQ-07 | Verificado en casos | Matriz de ocho puntos de integración; respuestas válidas, ausentes, inválidas y fallos clasificados; cifrado/firma Push comprobados. [E23](#e23). | P06 completado para los proveedores definidos; un proveedor nuevo exige contrastar su contrato y credenciales. |
| EQ-08 | Pendiente | Existe API documentada y página técnica de Web Push. Revisión de alcance. | P03: falta la interfaz final y la prueba con participantes y ambas vistas. |
| EQ-09 | Parcial | Mismos resultados en sesiones HTTP independientes con datos y reloj iguales. [E11](#e11). | P08: falta contraste visual entre celular y computadora con la interfaz final. |
| EQ-10 | Verificado en casos | Cuatro adaptadores HTTP sustituidos por archivo; ocho respuestas completas equivalentes, idempotencia y revisión de 135 fuentes del núcleo sin violaciones. [E24](#e24). | P07 completado para esta sustitución; dominio y aplicación sin cambios. |

## Trabajos identificados y estado

| ID | Trabajo y estado | Referencias | Resultado o criterio de comprobación |
|---|---|---|---|
| P01 | Lecturas de intervalo — completado | RF10, EQ-03 | Incorporación, suma exacta, huecos, superposiciones, duplicados, límites y mezcla verificados en [E19](#e19). |
| P02 | Comparación de costos — completado | RF20 | Diferencias, signo, fechas, duraciones, tarifas y datos parciales o ausentes verificados en [E20](#e20). |
| P03 | Interfaz, gráficos y usabilidad — pendiente de frontend | RF21, EQ-08, RC01 | Implementar las vistas de consulta y administración; representar huecos y distinguir importes; ejecutar las tareas propuestas con cinco participantes y las dos resoluciones. |
| P04 | Transporte HTTPS — completado | EQ-06 | Perfiles HTTPS de ambos servicios y comprobaciones reales con certificado validado. [E22](#e22). |
| P05 | Escalabilidad controlada — completado | EQ-02 | Comparación con afinidad de CPU, mismos datos/JAR y ocho consultas; ampliado cumple 5000 usuarios/1800 s con continuidad. [E25](#e25). |
| P06 | Contratos externos — completado | EQ-07 | Matriz consolidada y casos controlados de agua, identidad y Web Push, con referencias oficiales y sus límites. [E23](#e23). |
| P07 | Sustitución del adaptador — completado | EQ-10 | Sustitución desde archivo con resultados equivalentes, idempotencia y límites de dependencia verificados. [E24](#e24). |
| P08 | Dispositivos y recepción visual — pendiente de frontend | RF26, EQ-09, RC07 | Comparar valores y fechas desde celular y computadora y documentar Web Push con aplicación cerrada, permiso y conectividad en los dispositivos/navegadores que se declaren admitidos. |

P01, P02, P04, P05, P06 y P07 están completados para los casos y entornos
documentados. P03 y P08 corresponden al frontend y a su comprobación con
usuarios y dispositivos. La configuración de un despliegue público se
evalúa cuando se eligen su dominio, certificado y recursos.

## Registro de evidencias

### E01

[verificacion-acceso.md](verificacion-acceso.md).

[SesionesConsultaApiTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/sesiones/SesionesConsultaApiTests.java); métodos de referencia: `descartaEntradasInvalidasAntesDeContactarFuentes`, `iniciaConUnaPersonaDistintaAlTitularYRegistraElAccesoCompleto`, `caducaEnLaFechaFijadaAunqueHayaActividad`.

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `consultaAmbasFuentesHttpConLosDatosCorrectosYRegistraElAcceso`, `losFallosDeIdentidadInterrumpenElInicioSinExponerDatos`.

### E02

[recuperacion-lecturas.md](recuperacion-lecturas.md).

[RepositorioLecturasJdbcTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/lecturas/infraestructura/persistencia/RepositorioLecturasJdbcTests.java); métodos de referencia: `guardaUnaLecturaCompletaYConservaSuFechaUtc`, `repetirUnaLecturaNoLaCuentaOtraVezNiActualizaLaRecepcionOriginal`, `unIdConOtrosDatosGeneraIncidenciaSinReemplazarElAceptado`, `unFalloAlGuardarUnaIncidenciaRevierteLasLecturasNuevasDelMismoLote`.

### E03

[sincronizacion-lecturas.md](sincronizacion-lecturas.md).

[SincronizarLecturasTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/lecturas/sincronizacion/SincronizarLecturasTests.java); métodos de referencia: `completoEsperaHastaElProximoIntervalo`, `reintentoExponencialPersisteEntreInstanciasYRespetaElMaximo`, `falloDeUnaFuenteNoImpideAtenderOtroSuministro`.

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `flujoAdministrativoRecuperaLecturasYRecibosConCalendarioPublicado`.

### E04

[verificacion-consumo.md](verificacion-consumo.md).

[ConsumoPeriodoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/lecturas/dominio/ConsumoPeriodoTests.java); métodos de referencia: `calculaLaDiferenciaExactaYConservaLasReferencias`, `diferenciaAusenciaDeLecturasDeConsumoCero`, `rechazaElCambioDeMedidorYElRetrocesoAunqueLosExtremosSuban`.

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `consumoHistorialYEstadisticasCoincidenAlCambiarElMesDeLima`.

Fuente revisada: [LecturaAcumulada.java](../src/main/java/pe/nivora/backend/lecturas/dominio/LecturaAcumulada.java).

### E05

[estadisticas-consumo.md](estadisticas-consumo.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `estadisticasComparanLosTramosYCalculanElResumenSoloConMesesCompletos`, `estadisticasSinMesesCompletosConservanLaSerieYDejanLosTotalesIndefinidos`.

Fuente revisada: [ConsultarEstadisticasConsumo.java](../src/main/java/pe/nivora/backend/lecturas/aplicacion/ConsultarEstadisticasConsumo.java).

Fuente revisada: [ConsultarCostosConsumo.java](../src/main/java/pe/nivora/backend/tarifas/aplicacion/ConsultarCostosConsumo.java).

### E06

[verificacion-tarifas.md](verificacion-tarifas.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `costosUsanElConsumoYLaVersionAplicableDeCadaMes`, `costosIdentificanTarifasIncompatiblesYConceptosNoInformados`, `tarifasImportadasYPublicadasSeAplicanAlCambiarDeMesEnLima`.

### E07

[proyeccion-mensual.md](proyeccion-mensual.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `proyeccionUsaLaUltimaMedicionYAplicaLosTramosAlVolumenDelMes`, `proyeccionDistingueFaltaDeLecturasDeFaltaDeTarifaYConceptos`.

### E08

[verificacion-recibos.md](verificacion-recibos.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `recibosEmitidosSeSincronizanYSePuedenConsultarDuranteUnaCaida`.

[AlmacenamientoRecibosTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/recibos/infraestructura/AlmacenamientoRecibosTests.java); métodos de referencia: `guardaElReciboEmitidoYRepetirloConservaElImporteYLaRecepcion`, `avisaPorRecibosGuardadosCuandoLlegaSuFechaEnLima`.

### E09

[configuracion-notificaciones.md](configuracion-notificaciones.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `preferenciasPorDispositivoYSuministroRequierenPermisoParaActivar`, `pausaPersistidaSeAplicaAlEvaluadorYPermiteReanudarSinReinicio`, `alertaSoloAlSuperarElMesAnteriorYConservaLaPrimeraDeteccion`, `alertaRequiereCoberturaCompletaDelMesAnterior`, `bandejaMarcaLeidaUnaVezYConservaLaFechaEntreSesiones`.

### E10

[verificacion-continuidad-avisos.md](verificacion-continuidad-avisos.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `consultasYBandejaContinuanDuranteFallosDePushYDeLaEmpresa`, `desactivarUnDispositivoDetieneLosReintentosYConservaLaBandeja`.

[ColaWebPushTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/notificaciones/infraestructura/ColaWebPushTests.java); métodos de referencia: `reintentaYConservaUnSoloRegistroPorAviso`, `agotaSeisIntentosTemporalesYConservaLaEntregaFallidaSinDuplicarla`, `unaReservaVencidaNoPuedeConfirmarLaEntregaReservadaPorOtroTrabajador`.

[EmisorWebPushHttpTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/notificaciones/infraestructura/EmisorWebPushHttpTests.java); métodos de referencia: `preparaUnaPeticionCifradaConVapidYTopicEstable`, `clasificaAceptacionExpiracionYReintentos`.

### E11

[verificacion-contratos-api.md](verificacion-contratos-api.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `contratoDeConsultaConservaElSuministroYEquivaleEntreSesiones`, `contratoAdministrativoRequierePermisoAntesDeConsultarOEscribir`, `laSesionAjenaNoPuedeMarcarAvisosNiRetirarLaSuscripcionDelOtroSuministro`.

[GestionAdministradoresApiTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/administradores/GestionAdministradoresApiTests.java); métodos de referencia: `laGestionExigeSesionYPermisoEnTodasLasRutas`, `ultimaCuentaConGestionNoSeDesactivaNiPierdeElPermiso`.

### E12

[periodos-mensuales.md](periodos-mensuales.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `publicaPeriodosFuturosYConservaLasRevisiones`, `elCorteConfiguradoSeAplicaAlConsumoEIdentificaLosRecibosConRangosDistintos`.

[CalendarioMensualTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/periodos/dominio/CalendarioMensualTests.java); métodos de referencia: `ajustaElDiaAlUltimoDeFebreroYConservaUnLimiteComun`.

### E13

[gestion-tarifas.md](gestion-tarifas.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `publicacionTarifariaConservaLaRevisionYLosExtremosDeLaVigenciaAnterior`, `gestionTarifariaExigePermisoYValidaFechasTramosYPrecision`.

[TarifasIntegracionTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/tarifas/TarifasIntegracionTests.java); métodos de referencia: `unCambioDeVigenciaConservaLasVersionesYSeleccionaPorIntervalo`, `fallaElCambioDeImportesEnUnaVersionExistenteYRevierteElLoteCompleto`.

### E14

[verificacion-administracion.md](verificacion-administracion.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `paginaAccesosPorSuministroYProtegeElDni`, `supervisionDeRecibosDistingueReservaVigenteYVencida`, `incidenciasConservaEmpatesAlPaginarYAislaCadaSuministro`, `flujoAdministrativoRecuperaLecturasYRecibosConCalendarioPublicado`.

### E15

[reintentos-integracion.md](reintentos-integracion.md).

[AccesoConsultaIntegradoTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/AccesoConsultaIntegradoTests.java); métodos de referencia: `reintentoDeRecibosAdelantaSoloSuCalendarioYConservaElUltimoResultado`, `reintentoDeLecturasRespetaElBloqueoYLaReservaDelTrabajador`.

### E16

[resultados-carga-github-actions.md](resultados-carga-github-actions.md).

Fuente revisada: [README.md](../../empresa-agua/README.md).

### E17

[verificacion-acceso.md](verificacion-acceso.md).

[FuenteSuministrosHttpTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/empresaagua/FuenteSuministrosHttpTests.java); métodos de referencia: `devuelveVacioSoloAnteAusenciaConfirmadaPorElContrato`, `noConvierteErroresNiRespuestasInvalidasEnAusencia`.

[FuenteIdentidadApisPeruTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/acceso/infraestructura/identidad/FuenteIdentidadApisPeruTests.java); métodos de referencia: `consultaLaRutaCompletaConBearerYConservaLosCerosIniciales`, `informaUnaConsultaNoConfirmadaSinExponerElCuerpo`.

[FuenteLecturasHttpTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/lecturas/infraestructura/empresaagua/FuenteLecturasHttpTests.java); métodos de referencia: `consultaYTraduceUnaPaginaCompleta`, `rechazaUnaPaginaIncompatibleCompleta`, `limitaLaEsperaDelCuerpoDeLaPagina`.

[FuenteRecibosHttpTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/recibos/infraestructura/FuenteRecibosHttpTests.java); métodos de referencia: `reportaFalloDeLaFuenteAnteUnaRespuestaInconsistente`.

### E18

[verificacion-documentacion-api.md](verificacion-documentacion-api.md).

[DocumentacionApiTests](../../../tecnologia/nivora-backend/src/test/java/pe/nivora/backend/documentacion/DocumentacionApiTests.java); métodos de referencia: `documentaTodasLasOperacionesRegistradasSinInventarRutas`, `documentaParametrosRealesConObligatoriedadYValoresPredeterminados`, `conservaLosCamposYTiposDeLosRegistrosYEnumsPublicados`.

La recepción visual mencionada en RF26 fue confirmada manualmente por el
responsable en Windows/Chrome el 16 de noviembre de 2026, con la página de
Nivora cerrada. Esa observación no es una prueba automatizada de entrega ni
una matriz completa de compatibilidad. El procedimiento está en
[Web Push](web-push.md).

### E19

[Lecturas de intervalo](lecturas-intervalo.md) y
[evidencia de la ampliación](evidencias/lecturas-intervalo-2026-11-19.json).
Incluye `ConsumoIntervalosTests`, `LecturasIntervaloJdbcTests`, respuestas
de intervalo de `FuenteLecturasHttpTests`, reanudación en
`RecuperacionConCorteTests` y los seis métodos `intervalos*` de
`AccesoConsultaIntegradoTests`. Los métodos exactos y las huellas de sus
fuentes se conservan en el resumen estructurado.

### E20

[Comparación de consumo y costos](comparacion-costos.md) y
[evidencia de RF20](evidencias/comparacion-costos-2026-11-19.json).
Incluye `ComparacionCostosTests`, los 21 casos `comparacion*` y la octava
operación de la matriz de contratos HTTP. Los dos casos de continuidad
existentes también incluyen la nueva ruta. Los 31 casos HTTP seleccionados
pasaron con MySQL 8.0.46 aislado.

### E21

[Incorporación y actualización automática de tarifas](sincronizacion-tarifas.md) y
[evidencia de la corrección](evidencias/sincronizacion-tarifas-2026-11-19.json).
Incluye `SincronizacionTarifasTests`, `TarifasAutomaticasTests` y los seis
casos HTTP `tarifasAutomaticas*`/`elRefrescoAutomatico*` de
`AccesoConsultaIntegradoTests`. Comprueba el primer historial después de un
alta, refresco, reintentos persistidos, reservas concurrentes y vencidas,
conservación ante fallos/conflictos y prioridad de tarifas administrativas.
La migración 22 añade el calendario sin modificar los historiales existentes.

### E22

[Despliegue con HTTPS](verificacion-https.md). `TransporteHttpsTests` del
backend y de la Empresa de Agua. Certificados temporales con validación de
confianza y nombre; dos protocolos, permisos, revocación y fuentes HTTPS.

### E23

[Matriz de contratos externos](contratos-externos.md).
`ContratosEmpresaAguaTests`, `FuenteIdentidadApisPeruTests` y
`EmisorWebPushHttpTests`, además de las comprobaciones de persistencia y
continuidad existentes. Incluye descifrado independiente del cuerpo Push.

### E24

[Sustitución controlada](sustitucion-adaptadores.md).
`SustitucionAdaptadoresTests`, `AdaptadoresAguaArchivo` y
`DependenciasArquitecturaTests`. Se conserva el núcleo de `890db94`.

### E25

[Escalabilidad controlada y carga actual](escalabilidad-controlada.md),
[evidencia consolidada](evidencias/cierre-backend-2026-11-19.json) y JSON
por etapa/operación. Mismo conjunto y JAR, ampliación de CPU, ocho consultas,
lecturas acumuladas/de intervalo, sincronización tarifaria y alertas activas.
