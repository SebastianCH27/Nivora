package pe.nivora.backend.tarifas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;
import pe.nivora.backend.tarifas.aplicacion.SincronizarTarifas;
import pe.nivora.backend.tarifas.dominio.FuenteTarifasNoDisponibleException;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas.Estado;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas.Reserva;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;
import pe.nivora.backend.tarifas.infraestructura.RepositorioSincronizacionTarifasJdbc;
import pe.nivora.backend.tarifas.infraestructura.RepositorioTarifasJdbc;
import static org.junit.jupiter.api.Assertions.*;

class SincronizacionTarifasTests {
    private static final Instant AHORA = Instant.parse("2026-11-11T13:00:00Z");
    private static final CodigoSuministro CODIGO = new CodigoSuministro("000101");
    private static final CodigoSuministro OTRO = new CodigoSuministro("000102");
    private static final Duration RESERVA = Duration.ofSeconds(60);
    private static final SincronizarTarifas.Politica POLITICA = new SincronizarTarifas.Politica(2, RESERVA,
        Duration.ofSeconds(300), Duration.ofSeconds(30), Duration.ofSeconds(120));
    private final AtomicReference<Instant> instante = new AtomicReference<>();
    private final AtomicInteger llamadas = new AtomicInteger();
    private final AtomicReference<Function<CodigoSuministro, Optional<HistorialTarifario>>> respuesta = new AtomicReference<>();
    private DriverManagerDataSource datos;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager gestor;
    private RepositorioTarifasJdbc tarifas;
    private RepositorioSincronizacionTarifasJdbc programacion;
    private Clock reloj;

    @BeforeEach void preparar() {
        datos = new DriverManagerDataSource("jdbc:h2:mem:tarifas_sync_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora", "sa", "");
        jdbc = new JdbcTemplate(datos);
        gestor = new DataSourceTransactionManager(datos);
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
            new FileSystemResource("base-datos/10-tarifas.sql"),
            new FileSystemResource("base-datos/22-sincronizacion-tarifas.sql")).execute(datos);
        tarifas = new RepositorioTarifasJdbc(jdbc, gestor, new JsonMapper());
        programacion = nuevaProgramacion();
        instante.set(AHORA);
        llamadas.set(0);
        reloj = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zona) { return Clock.fixed(instant(), zona); }
            @Override public Instant instant() { return instante.get(); }
        };
        respuesta.set(codigo -> Optional.of(historial(codigo, 1, "5.00")));
        registrar(CODIGO);
    }

    @AfterEach void cerrar() { jdbc.execute("SHUTDOWN"); }

    @Test void descubreAltasSinImportacionManualYRespetaElLimiteDelCiclo() {
        registrar(OTRO);
        registrar(new CodigoSuministro("000103"));
        assertEquals(2, trabajador().ejecutarCiclo().size());
        assertEquals(2, llamadas.get());
        assertTrue(tarifas.buscar("empresa-agua", CODIGO).isPresent());
        assertTrue(tarifas.buscar("empresa-agua", OTRO).isPresent());
        assertEquals("000103", trabajador().ejecutarCiclo().getFirst().reserva().suministro().valor());
        assertTrue(trabajador().ejecutarCiclo().isEmpty());
    }

    @Test void refrescaSinDuplicarRevisionesYConservaLasVersionesAlCambiarLaTarifa() {
        var inicial = trabajador().ejecutarCiclo().getFirst();
        assertEquals(Estado.ACTUALIZADO, inicial.estado());
        assertEquals(AHORA.plusSeconds(300), inicial.proximoIntento());
        instante.set(AHORA.plusSeconds(299));
        assertTrue(trabajador().ejecutarCiclo().isEmpty());
        instante.set(AHORA.plusSeconds(300));
        assertEquals(1L, trabajador().ejecutarCiclo().getFirst().guardado().revision());
        respuesta.set(c -> Optional.of(historial(c, 2, "9.00")));
        instante.set(AHORA.plusSeconds(600));
        assertEquals(2L, trabajador().ejecutarCiclo().getFirst().guardado().revision());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas_historiales", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas_versiones", Integer.class));
        assertEquals(new BigDecimal("5.00"), jdbc.queryForObject(
            "SELECT cargo_fijo FROM tarifas_versiones WHERE version_tarifa = 1", BigDecimal.class));
    }

    @Test void unaCaidaConservaTarifasYElReintentoPersisteEntreInstancias() {
        trabajador().ejecutarCiclo();
        var anterior = tarifas.buscar("empresa-agua", CODIGO).orElseThrow();
        respuesta.set(c -> { throw new FuenteTarifasNoDisponibleException(); });
        instante.set(AHORA.plusSeconds(300));
        for (long espera : new long[] {30, 60, 120, 120}) {
            var fallo = trabajador().ejecutarCiclo().getFirst();
            assertEquals(Estado.ERROR_FUENTE, fallo.estado());
            assertTrue(fallo.programacionConfirmada());
            assertEquals(instante.get().plusSeconds(espera), fallo.proximoIntento());
            assertEquals(anterior, tarifas.buscar("empresa-agua", CODIGO).orElseThrow());
            instante.set(fallo.proximoIntento().minusSeconds(1));
            assertTrue(trabajador().ejecutarCiclo().isEmpty());
            instante.set(fallo.proximoIntento());
        }
        respuesta.set(c -> Optional.of(historial(c, 1, "5.00")));
        var recuperado = trabajador().ejecutarCiclo().getFirst();
        assertEquals(Estado.ACTUALIZADO, recuperado.estado());
        assertEquals(0, jdbc.queryForObject("SELECT fallos_consecutivos FROM programacion_sincronizacion_tarifas", Integer.class));
        assertEquals(instante.get().atOffset(ZoneOffset.UTC).toLocalDateTime(), jdbc.queryForObject(
            "SELECT ultimo_exito_utc FROM programacion_sincronizacion_tarifas", LocalDateTime.class));
    }

    @Test void laAusenciaConfirmadaNoBorraElHistorialYPermiteRecuperarloDespues() {
        trabajador().ejecutarCiclo();
        var anterior = tarifas.buscar("empresa-agua", CODIGO).orElseThrow();
        instante.set(AHORA.plusSeconds(300));
        respuesta.set(c -> Optional.empty());
        assertEquals(Estado.SUMINISTRO_NO_ENCONTRADO, trabajador().ejecutarCiclo().getFirst().estado());
        assertEquals(anterior, tarifas.buscar("empresa-agua", CODIGO).orElseThrow());
        assertEquals(AHORA.atOffset(ZoneOffset.UTC).toLocalDateTime(), jdbc.queryForObject(
            "SELECT ultimo_exito_utc FROM programacion_sincronizacion_tarifas", LocalDateTime.class));
    }

    @Test void unCambioInmutableFallaSinPerderElHistorialNiImpedirOtroSuministro() {
        trabajador().ejecutarCiclo();
        var anterior = tarifas.buscar("empresa-agua", CODIGO).orElseThrow();
        instante.set(AHORA.plusSeconds(300));
        registrar(OTRO);
        respuesta.set(c -> Optional.of(historial(c, c.equals(CODIGO) ? 1 : 2, "9.00")));
        var intentos = trabajador().ejecutarCiclo();
        assertEquals(List.of(Estado.CONFLICTO, Estado.ACTUALIZADO), intentos.stream().map(SincronizarTarifas.Intento::estado).toList());
        assertEquals(anterior, tarifas.buscar("empresa-agua", CODIGO).orElseThrow());
        assertTrue(tarifas.buscar("empresa-agua", OTRO).isPresent());
    }

    @Test void unaFuenteQueRespondePorOtroSuministroNoCreaTarifasAjena() {
        respuesta.set(c -> Optional.of(historial(OTRO, 1, "5.00")));
        assertEquals(Estado.ERROR_FUENTE, trabajador().ejecutarCiclo().getFirst().estado());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas_historiales", Integer.class));
    }

    @Test void elTransporteSeEjecutaFueraDeUnaTransaccionSql() {
        respuesta.set(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(historial(c, 1, "5.00"));
        });
        assertEquals(Estado.ACTUALIZADO, trabajador().ejecutarCiclo().getFirst().estado());
    }

    @Test void unFalloJdbcDeImportacionSeProgramaComoErrorDeAlmacenamiento() {
        jdbc.execute("ALTER TABLE tarifas_versiones RENAME TO tarifas_versiones_no_disponible");
        assertEquals(Estado.ERROR_ALMACENAMIENTO, trabajador().ejecutarCiclo().getFirst().estado());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas_historiales", Integer.class));
    }

    @Test void repetirLaMigracionYPreparacionConservaElCalendarioYLaReserva() {
        programacion.prepararSuministros(AHORA);
        var reserva = reservar(AHORA);
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/22-sincronizacion-tarifas.sql")).execute(datos);
        nuevaProgramacion().prepararSuministros(AHORA.plusSeconds(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM programacion_sincronizacion_tarifas", Integer.class));
        assertEquals(reserva.token(), jdbc.queryForObject("SELECT reserva_token FROM programacion_sincronizacion_tarifas", String.class));
        assertTrue(nuevaProgramacion().reservar(AHORA, AHORA, RESERVA).isEmpty());
    }

    @Test void unaReservaVencidaPuedeRecuperarseSinQueSuPropietarioAnteriorLaSobrescriba() {
        programacion.prepararSuministros(AHORA);
        var antigua = reservar(AHORA);
        instante.set(AHORA.plusSeconds(61));
        assertFalse(programacion.finalizar(antigua, Estado.ACTUALIZADO, 0, instante.get().plusSeconds(300), instante.get()));
        var nueva = reservar(instante.get());
        assertNotEquals(antigua.token(), nueva.token());
        assertFalse(programacion.finalizar(antigua, Estado.ACTUALIZADO, 0, instante.get().plusSeconds(300), instante.get()));
        assertTrue(programacion.finalizar(nueva, Estado.ACTUALIZADO, 0, instante.get().plusSeconds(300), instante.get()));
    }

    @Test void dosInstanciasNoReservanElMismoSuministro() throws Exception {
        programacion.prepararSuministros(AHORA);
        var inicio = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarea = () -> { inicio.await(); return nuevaProgramacion().reservar(AHORA, AHORA, RESERVA).isPresent(); };
            var a = hilos.submit(tarea);
            var b = hilos.submit(tarea);
            inicio.countDown();
            assertEquals(1, (a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rechazaPoliticasQueNoPermitenUnCalendarioAcotado(int caso) {
        assertThrows(IllegalArgumentException.class, () -> new SincronizarTarifas.Politica(
            caso == 0 ? 0 : caso == 1 ? 1001 : 2,
            caso == 2 ? Duration.ZERO : caso == 3 ? Duration.ofMillis(1500) : RESERVA,
            caso == 4 ? Duration.ofDays(2) : Duration.ofSeconds(300),
            caso == 5 ? Duration.ofSeconds(121) : Duration.ofSeconds(30), Duration.ofSeconds(120)));
    }

    private void registrar(CodigoSuministro codigo) {
        jdbc.update("INSERT INTO suministros VALUES (?, 'Titular ficticio', ?)", codigo.valor(), LocalDateTime.ofInstant(instante.get(), ZoneOffset.UTC));
    }

    private Reserva reservar(Instant ahora) { return nuevaProgramacion().reservar(ahora, ahora, RESERVA).orElseThrow(); }

    private RepositorioSincronizacionTarifasJdbc nuevaProgramacion() { return new RepositorioSincronizacionTarifasJdbc(jdbc, gestor); }

    private SincronizarTarifas trabajador() {
        return new SincronizarTarifas(nuevaProgramacion(), new IncorporarTarifas(c -> {
            llamadas.incrementAndGet();
            return respuesta.get().apply(c);
        }, tarifas, reloj), POLITICA, reloj);
    }

    private static HistorialTarifario historial(CodigoSuministro codigo, int version, String cargo) {
        var desde = LocalDate.parse("2026-10-01");
        var tarifa = new VersionTarifaria("DOMESTICA", version, "Demostracion", desde, new BigDecimal(cargo),
            BigDecimal.ZERO, List.of(new VersionTarifaria.Tramo(BigDecimal.ZERO, null, new BigDecimal("2.50"), BigDecimal.ZERO)));
        return new HistorialTarifario("empresa-agua", codigo, List.of(new HistorialTarifario.Asignacion(tarifa, null, desde, null)));
    }
}
