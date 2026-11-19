# Matriz de revisión de requisitos del backend

Bloque 9, parte 5. Revisión del 19 de noviembre de 2026 (America/Lima),
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
concretos y el inventario de la última regresión se conservan en
[el resumen estructurado](evidencias/revision-final-backend-2026-11-19.json).
Las reservas P01–P08 identifican trabajos posteriores, sin modificar los
requisitos originales.

## Requisitos funcionales

| ID | Función | Estado en esta revisión | Alcance comprobado y reserva | Evidencia |
|---|---|---|---|---|
| RF01 | Entrada de suministro y DNI | Backend comprobado | Valida formato, tipos y presencia antes de invocar las fuentes. | [E01](#e01) |
| RF02 | Comprobación externa de suministro e identidad | Backend comprobado | Distingue inexistencia confirmada, credencial/cuota, respuesta incompatible y caída; no crea sesión sin validación. | [E01](#e01), [E17](#e17) |
| RF03 | Sesión asociada al suministro | Backend comprobado | Inicio, consulta, cierre, caducidad y aislamiento entre suministros. | [E01](#e01), [E11](#e11) |
| RF04 | Registro del acceso | Backend comprobado | Conserva suministro, identidad y fecha; restringe la consulta administrativa y enmascara el DNI. | [E01](#e01), [E14](#e14) |
| RF05 | Recuperación automática de lecturas | Backend comprobado | Programación persistida, lotes acotados y reintentos separados de las consultas. | [E03](#e03) |
| RF06 | Registro de lecturas | Backend comprobado | Conserva fuente, identificador, medidor, fecha y recepción para las lecturas acumuladas en litros admitidas. El soporte de otro tipo de medición se revisa en RF10. | [E02](#e02) |
| RF07 | Validación de lecturas e incidencias | Backend comprobado | Rechaza datos inválidos y registra motivos; preserva registros aceptados ante conflictos. Alcance: contrato acumulado de la demostración. | [E02](#e02), [E17](#e17) |
| RF08 | Detección de duplicados | Backend comprobado | Repetición, identidad de la medición y concurrencia no agregan una segunda copia. | [E02](#e02) |
| RF09 | Recuperación tras fallos | Backend comprobado | Conserva progreso y lecturas aceptadas y reanuda el recorrido sin doble conteo. | [E03](#e03), [E14](#e14) |
| RF10 | Cálculo según tipo de medición | Parcial | La diferencia de contadores acumulados está implementada y probada. P01: falta el modelo, incorporación y suma de consumos de intervalos no superpuestos. | [E04](#e04) |
| RF11 | Litros y metros cúbicos | Backend comprobado | La API entrega ambas magnitudes con conversión decimal explícita. La selección y presentación visual corresponden al frontend. | [E04](#e04) |
| RF12 | Acumulado mensual y fechas | Backend comprobado | Usa los límites del calendario configurado y las fechas efectivamente medidas. | [E04](#e04), [E12](#e12) |
| RF13 | Cobertura e incompatibilidades | Backend comprobado | Distingue SIN_DATOS, PARCIAL, COMPLETO e INCOMPATIBLE; un dato ausente no se convierte en cero. | [E04](#e04) |
| RF14 | Última medición y actualización | Backend comprobado | Publica fecha de medición y estado aun cuando falle la fuente o empiece un mes sin lecturas. | [E04](#e04), [E14](#e14) |
| RF15 | Costo acumulado estimado | Backend comprobado | Aplica la vigencia y versión tarifaria al consumo calculable del periodo. | [E06](#e06) |
| RF16 | Detalle del costo estimado | Backend comprobado | Entrega consumo, selección tarifaria y conceptos; indica conceptos faltantes. | [E06](#e06) |
| RF17 | Proyección de consumo y costo | Backend comprobado | Proyecta con el ritmo observado, duración mensual y tramos de la tarifa aplicable. | [E06](#e06), [E07](#e07) |
| RF18 | Fecha, supuestos e insuficiencia | Backend comprobado | Expone fecha y supuesto de ritmo constante; diferencia insuficiencia de lecturas, tarifa y conceptos. | [E07](#e07) |
| RF19 | Consumo y costos anteriores | Backend comprobado | Devuelve periodos anteriores con cobertura, consumo y estimación versionada. | [E04](#e04), [E06](#e06) |
| RF20 | Comparación entre periodos | Parcial | Estadísticas devuelve fechas, duración y diferencia de volumen; costos entrega estimaciones por periodo. P02: falta completar y probar la diferencia de costo estimado entre los periodos comparados. | [E05](#e05), [E06](#e06) |
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
| EQ-01 | Verificado en entorno | 5000 sesiones durante 1800 s; p95 por operación 1793–1867 ms, cero errores y continuidad de sincronización/alertas. [E16](#e16). | Montaje ficticio compartido, carga cerrada y JAR de 2d43391; no extrapolar al despliegue final. |
| EQ-02 | Parcial | Dos perfiles completos con pools 10 y 20. [E16](#e16). | P05: hosts diferentes; falta comparación controlada de ampliación de recursos. |
| EQ-03 | Parcial | Validación, unicidad, acumulados, costos y proyecciones con resultados esperados. [E02](#e02), [E04](#e04), [E06](#e06), [E07](#e07). | P01: faltan lecturas de intervalo. |
| EQ-04 | Verificado en casos | Las consultas locales y avisos se conservan durante caídas y los reintentos recuperan pendientes. [E03](#e03), [E08](#e08), [E10](#e10), [E14](#e14). | Casos definidos con fuentes y emisor controlados; no es una certificación general de disponibilidad. |
| EQ-05 | Verificado en casos | Fechas, referencias de lecturas, vigencias, revisiones y supuestos comprobados. [E04](#e04), [E06](#e06), [E07](#e07), [E12](#e12). | Alcance de las lecturas y consultas implementadas. |
| EQ-06 | Parcial | Sesiones, caducidad, revocación, permisos y protección de registros comprobados. [E01](#e01), [E11](#e11), [E14](#e14). | P04: falta comprobar comunicaciones HTTPS en un despliegue de pruebas. |
| EQ-07 | Parcial | Hay pruebas de adaptadores HTTP para agua e identidad y preparación/clasificación de Web Push. [E10](#e10), [E13](#e13), [E17](#e17). | P06: completar matriz de contratos externos y compatibilidad con cada proveedor al conectarlo. |
| EQ-08 | Pendiente | Existe API documentada y página técnica de Web Push. Revisión de alcance. | P03: falta la interfaz final y la prueba con participantes y ambas vistas. |
| EQ-09 | Parcial | Mismos resultados en sesiones HTTP independientes con datos y reloj iguales. [E11](#e11). | P08: falta contraste visual entre celular y computadora con la interfaz final. |
| EQ-10 | Pendiente | Existen contratos y separación de dominio, aplicación e infraestructura. Revisión de alcance. | P07: falta sustitución controlada de adaptador, revisión de dependencias y regresión del cambio. |

## Pendientes con criterio de comprobación

| ID | Trabajo | Referencias | Para considerarlo verificado |
|---|---|---|---|
| P01 | Lecturas de intervalo | RF10, EQ-03 | Incorporar intervalos con límites, unidades y suministro; sumar solo intervalos no superpuestos; probar huecos, duplicados, solapes y mezcla con acumulados sin doble conteo. |
| P02 | Comparación de costos | RF20 | Definir y devolver la diferencia estimada con los periodos, duraciones y revisiones comparadas; comprobar datos parciales y conceptos o tarifas insuficientes. |
| P03 | Interfaz, gráficos y usabilidad | RF21, EQ-08, RC01 | Implementar las vistas de consulta y administración; representar huecos y distinguir importes; ejecutar las tareas propuestas con cinco participantes y las dos resoluciones. |
| P04 | Transporte HTTPS | EQ-06 | Preparar un despliegue de pruebas y comprobar TLS y las comunicaciones protegidas desde sus clientes. |
| P05 | Escalabilidad controlada | EQ-02 | Comparar recursos iniciales y ampliados con el mismo JAR, datos, mezcla y generador controlado; medir capacidad sostenible y recursos sin atribuir diferencias entre hosts solo al pool. |
| P06 | Contratos externos | EQ-07 | Consolidar la matriz por adaptador de solicitudes, respuestas válidas, ausencia, formato inválido y fallos; contrastar con la especificación del proveedor antes de cada conexión real. |
| P07 | Sustitución del adaptador | EQ-10 | Sustituir una implementación conservando el contrato; registrar archivos/dependencias y demostrar regresión sin cambios de negocio motivados solo por el protocolo. |
| P08 | Dispositivos y recepción visual | RF26, EQ-09, RC07 | Comparar valores y fechas desde celular y computadora y documentar Web Push con aplicación cerrada, permiso y conectividad en los dispositivos/navegadores que se declaren admitidos. |

P01 y P02 son brechas funcionales del backend. P06 y P07 completan sus
evaluaciones de integración y mantenibilidad. P03 y P08 requieren las
interfaces y dispositivos finales. P04 y P05 requieren definir y evaluar
el despliegue correspondiente. Esta revisión conserva todos esos pendientes.

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
