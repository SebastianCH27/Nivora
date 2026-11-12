package pe.nivora.backend.recibos.infraestructura;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.aplicacion.SincronizarRecibos;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;
import pe.nivora.backend.recibos.dominio.ReciboEmitido;
import pe.nivora.backend.recibos.dominio.RecibosEmitidos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos.Incorporacion;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos.Estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlmacenamientoRecibosTests {
    private static final CodigoSuministro SUMINISTRO = new CodigoSuministro("000101");
    private static final CodigoSuministro OTRO = new CodigoSuministro("000102");
    private static final String RUTA = "/api/v1/suministros/000101/recibos";
    private static final Instant AHORA = Instant.parse("2026-12-11T13:00:00.123456789Z");
    private static final Instant RECIBIDO = Instant.parse("2026-12-11T13:00:00.123456Z");
    private static final String OCTUBRE = """
        {"numeroRecibo":"REC-101-10","periodo":"2026-10-01","consumoLitros":5637.125,
         "cargoFijo":5.00,"importeConsumo":8.46,"importeTotal":13.46,
         "fechaDisponibilidad":"2026-11-01","fechaVencimiento":"2026-11-08",
         "codigoTarifa":"DOMESTICA","versionTarifa":1}
        """;
    private static final String NOVIEMBRE = OCTUBRE
        .replace("REC-101-10", "REC-101-11").replace("2026-11-01", "2026-12-01")
        .replace("2026-10-01", "2026-11-01").replace("2026-11-08", "2026-12-08");
    private static final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private static final HttpServer empresa = iniciarEmpresa();

    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IncorporarRecibos incorporar;
    @Autowired private RepositorioRecibos repositorio;
    @Autowired private RepositorioSincronizacionRecibos programacion;
    private Connection esquema;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_recibos;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base", () -> "http://127.0.0.1:" + empresa.getAddress().getPort());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojPrueba {
        @Bean @Primary Clock relojRecibos() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        if (esquema == null) {
            esquema = datos.getConnection();
            new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
                new FileSystemResource("base-datos/12-recibos.sql"),
                new FileSystemResource("base-datos/13-sincronizacion-recibos.sql")).populate(esquema);
        }
        jdbc.execute("DELETE FROM programacion_sincronizacion_recibos");
        jdbc.execute("DELETE FROM recibos");
        jdbc.execute("DELETE FROM suministros");
        jdbc.update("""
            INSERT INTO suministros (codigo, titular, verificado_en_utc)
            VALUES ('000101', 'Persona ficticia', '2026-10-01 00:00:00'),
                   ('000102', 'Otra persona ficticia', '2026-10-01 00:00:00')
            """);
        publicar(OCTUBRE);
    }

    @AfterAll
    void cerrar() throws Exception {
        empresa.stop(0);
        if (esquema != null) esquema.close();
    }

    @Test
    void guardaElReciboEmitidoYRepetirloConservaElImporteYLaRecepcion() {
        assertEquals(new Incorporacion(1, 0), incorporar.ejecutar(SUMINISTRO, 12).orElseThrow());
        var guardado = repositorio.buscarRecientes(SUMINISTRO, 1).getFirst();
        var recibo = guardado.recibo();
        assertEquals("REC-101-10", recibo.numeroRecibo());
        assertEquals(YearMonth.of(2026, 10), recibo.periodo());
        assertEquals(new BigDecimal("5637.125"), recibo.consumoLitros());
        assertEquals(new BigDecimal("13.46"), recibo.importeTotal());
        assertEquals(LocalDate.parse("2026-11-01"), recibo.fechaDisponibilidad());
        assertEquals(LocalDate.parse("2026-11-08"), recibo.fechaVencimiento());
        assertEquals("DOMESTICA", recibo.codigoTarifa());
        assertEquals(RECIBIDO, guardado.recibidoEn());

        assertEquals(new Incorporacion(0, 1), incorporar.ejecutar(SUMINISTRO, 12).orElseThrow());
        assertEquals(new Incorporacion(0, 1),
            repositorio.guardar(new RecibosEmitidos(SUMINISTRO, List.of(recibo)), AHORA.plusSeconds(60)));
        assertEquals(List.of(guardado), repositorio.buscarRecientes(SUMINISTRO, 12));
        assertTrue(repositorio.buscarRecientes(OTRO, 12).isEmpty());
    }

    @Test
    void agregaUnPeriodoYConservaLosRecibosFueraDeLaVentanaDeOrigen() {
        incorporar.ejecutar(SUMINISTRO, 12).orElseThrow();
        publicar(NOVIEMBRE, OCTUBRE);
        assertEquals(new Incorporacion(1, 1), incorporar.ejecutar(SUMINISTRO, 12).orElseThrow());
        publicar(NOVIEMBRE);
        assertEquals(new Incorporacion(0, 1), incorporar.ejecutar(SUMINISTRO, 1).orElseThrow());
        assertEquals(List.of(YearMonth.of(2026, 11), YearMonth.of(2026, 10)),
            repositorio.buscarRecientes(SUMINISTRO, 12).stream().map(g -> g.recibo().periodo()).toList());
        assertEquals(1, repositorio.buscarRecientes(SUMINISTRO, 1).size());
    }

    @Test
    void ausenciaConfirmadaListaVaciaOFalloNoBorranLosRecibosGuardados() {
        incorporar.ejecutar(SUMINISTRO, 12).orElseThrow();
        var anterior = repositorio.buscarRecientes(SUMINISTRO, 12);
        publicar();
        assertEquals(new Incorporacion(0, 0), incorporar.ejecutar(SUMINISTRO, 12).orElseThrow());
        respuesta.set(new Respuesta(404, "application/problem+json", """
            {"status":404,"title":"Suministro no encontrado","instance":"%s"}
            """.formatted(RUTA)));
        assertTrue(incorporar.ejecutar(SUMINISTRO, 12).isEmpty());
        respuesta.set(new Respuesta(503, "application/problem+json", "{}"));
        assertThrows(FuenteRecibosNoDisponibleException.class, () -> incorporar.ejecutar(SUMINISTRO, 12));
        assertEquals(anterior, repositorio.buscarRecientes(SUMINISTRO, 12));
    }

    @Test
    void rechazaUnCambioAlReciboDelMismoPeriodoSinGuardarElRestoDelLote() {
        incorporar.ejecutar(SUMINISTRO, 12).orElseThrow();
        var anterior = repositorio.buscarRecientes(SUMINISTRO, 12);
        publicar(NOVIEMBRE, OCTUBRE.replace("8.46", "8.47").replace("13.46", "13.47"));
        assertThrows(RepositorioRecibos.Conflicto.class, () -> incorporar.ejecutar(SUMINISTRO, 12));
        assertEquals(anterior, repositorio.buscarRecientes(SUMINISTRO, 12));
    }

    @Test
    void revierteLasInsercionesDelLoteSiElNumeroPerteneceAOtroSuministro() {
        incorporar.ejecutar(SUMINISTRO, 12).orElseThrow();
        var repetido = repositorio.buscarRecientes(SUMINISTRO, 1).getFirst().recibo();
        var nuevo = reciboDePrueba("REC-102-11", YearMonth.of(2026, 11));
        var lote = new RecibosEmitidos(OTRO, List.of(nuevo, repetido));
        assertThrows(RepositorioRecibos.Conflicto.class, () -> repositorio.guardar(lote, AHORA));
        assertTrue(repositorio.buscarRecientes(OTRO, 12).isEmpty());
        assertEquals(1, repositorio.buscarRecientes(SUMINISTRO, 12).size());
    }

    @Test
    void requiereUnSuministroRegistradoAntesDeGuardar() {
        jdbc.update("DELETE FROM suministros WHERE codigo = ?", SUMINISTRO.valor());
        assertThrows(RepositorioRecibos.SuministroNoRegistrado.class, () -> incorporar.ejecutar(SUMINISTRO, 12));
        assertTrue(repositorio.buscarRecientes(SUMINISTRO, 12).isEmpty());
    }

    @Test
    void dosImportacionesSimultaneasDelMismoReciboGuardanUnaSolaCopia() throws Exception {
        var lote = new RecibosEmitidos(SUMINISTRO, List.of(reciboDePrueba("REC-101-10", YearMonth.of(2026, 10))));
        var listos = new CountDownLatch(2);
        Callable<Incorporacion> tarea = () -> {
            listos.countDown();
            if (!listos.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La otra importacion no inicio.");
            }
            return repositorio.guardar(lote, AHORA);
        };
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primera = hilos.submit(tarea);
            var segunda = hilos.submit(tarea);
            var a = primera.get(15, TimeUnit.SECONDS);
            var b = segunda.get(15, TimeUnit.SECONDS);
            assertEquals(1, a.nuevos() + b.nuevos());
            assertEquals(1, a.repetidos() + b.repetidos());
        }
        assertEquals(1, repositorio.buscarRecientes(SUMINISTRO, 12).size());
    }

    @Test
    void sincronizaYReintentaConEstadoPersistidoHastaRecuperarLaFuente() {
        jdbc.update("DELETE FROM suministros WHERE codigo = ?", OTRO.valor());
        var inicial = ciclo(RECIBIDO, 2).getFirst();
        assertEquals(Estado.ACTUALIZADO, inicial.estado());
        assertEquals(new Incorporacion(1, 0), inicial.incorporacion());
        var previo = repositorio.buscarRecientes(SUMINISTRO, 1).getFirst();
        Instant primerFallo = RECIBIDO.plusSeconds(300);
        assertEquals(primerFallo, inicial.proximoIntento());
        assertEquals(new Calendario("ACTUALIZADO", 0, RECIBIDO, primerFallo), calendario(SUMINISTRO));

        respuesta.set(new Respuesta(503, "application/problem+json", "{}"));
        assertTrue(ciclo(primerFallo.minusNanos(1000), 2).isEmpty());
        var fallido = ciclo(primerFallo, 2).getFirst();
        assertEquals(Estado.ERROR_FUENTE, fallido.estado());
        Instant segundoFallo = primerFallo.plusSeconds(30);
        assertEquals(segundoFallo, fallido.proximoIntento());
        assertEquals(new Calendario("ERROR_FUENTE", 1, RECIBIDO, segundoFallo), calendario(SUMINISTRO));
        assertEquals(List.of(previo), repositorio.buscarRecientes(SUMINISTRO, 60));

        ciclo(segundoFallo, 2);
        Instant reintento = segundoFallo.plusSeconds(60);
        assertEquals(new Calendario("ERROR_FUENTE", 2, RECIBIDO, reintento), calendario(SUMINISTRO));
        publicar(NOVIEMBRE, OCTUBRE);
        var recuperado = ciclo(reintento, 2).getFirst();
        assertEquals(Estado.ACTUALIZADO, recuperado.estado());
        assertEquals(new Incorporacion(1, 1), recuperado.incorporacion());
        assertEquals(new Calendario("ACTUALIZADO", 0, reintento, reintento.plusSeconds(300)),
            calendario(SUMINISTRO));
        assertEquals(previo, repositorio.buscarRecientes(SUMINISTRO, 60).getLast());
    }

    @Test
    void respetaElLimiteDelCicloYAislaElFalloDeOtroSuministro() {
        respuesta.set(new Respuesta(503, "application/problem+json", "{}"));
        var primero = ciclo(RECIBIDO, 1);
        assertEquals(1, primero.size());
        assertEquals(SUMINISTRO, primero.getFirst().reserva().suministro());
        assertEquals(Estado.ERROR_FUENTE, primero.getFirst().estado());
        var segundo = ciclo(RECIBIDO, 2);
        assertEquals(1, segundo.size());
        assertEquals(OTRO, segundo.getFirst().reserva().suministro());
        assertEquals(Estado.ACTUALIZADO, segundo.getFirst().estado());
        assertEquals(new Incorporacion(0, 0), segundo.getFirst().incorporacion());
    }

    @Test
    void unaReservaVencidaSeReemplazaYElPropietarioAnteriorNoPuedeConfirmarla() {
        jdbc.update("DELETE FROM suministros WHERE codigo = ?", OTRO.valor());
        programacion.prepararSuministros(RECIBIDO);
        var duracion = Duration.ofSeconds(60);
        var primera = programacion.reservar(RECIBIDO, RECIBIDO, duracion).orElseThrow();
        assertTrue(programacion.reservar(RECIBIDO, RECIBIDO, duracion).isEmpty());
        Instant vencimiento = primera.venceEn();
        var nueva = programacion.reservar(vencimiento, vencimiento, duracion).orElseThrow();
        assertFalse(programacion.finalizar(primera, Estado.ERROR_FUENTE, 1, vencimiento.plusSeconds(30), vencimiento));
        assertTrue(programacion.finalizar(nueva, Estado.ACTUALIZADO, 0, vencimiento.plusSeconds(300), vencimiento));
        assertEquals(new Calendario("ACTUALIZADO", 0, vencimiento, vencimiento.plusSeconds(300)),
            calendario(SUMINISTRO));
    }

    @Test
    void registraElConflictoSinReemplazarElReciboDefinitivo() {
        incorporar.ejecutar(SUMINISTRO, 12).orElseThrow();
        var original = repositorio.buscarRecientes(SUMINISTRO, 60);
        publicar(OCTUBRE.replace("8.46", "8.47").replace("13.46", "13.47"));
        assertEquals(Estado.CONFLICTO, ciclo(RECIBIDO, 1).getFirst().estado());
        assertEquals(new Calendario("CONFLICTO", 1, null, RECIBIDO.plusSeconds(30)), calendario(SUMINISTRO));
        assertEquals(original, repositorio.buscarRecientes(SUMINISTRO, 60));
    }

    private List<SincronizarRecibos.Intento> ciclo(Instant ahora, int intentos) {
        var politica = new SincronizarRecibos.Politica(intentos, 60, Duration.ofSeconds(60),
            Duration.ofSeconds(300), Duration.ofSeconds(30), Duration.ofSeconds(1800));
        return new SincronizarRecibos(programacion, incorporar, politica, Clock.fixed(ahora, ZoneOffset.UTC))
            .ejecutarCiclo();
    }

    private Calendario calendario(CodigoSuministro suministro) {
        return jdbc.queryForObject("""
            SELECT ultimo_resultado, fallos_consecutivos, ultimo_exito_utc, proximo_intento_utc
            FROM programacion_sincronizacion_recibos WHERE codigo_suministro = ?
            """, (fila, indice) -> {
                var exito = fila.getObject("ultimo_exito_utc", LocalDateTime.class);
                var siguiente = fila.getObject("proximo_intento_utc", LocalDateTime.class);
                return new Calendario(fila.getString("ultimo_resultado"), fila.getInt("fallos_consecutivos"),
                    exito == null ? null : exito.toInstant(ZoneOffset.UTC), siguiente.toInstant(ZoneOffset.UTC));
            }, suministro.valor());
    }

    private record Calendario(String resultado, int fallos, Instant ultimoExito, Instant proximoIntento) {}

    private static ReciboEmitido reciboDePrueba(String numero, YearMonth periodo) {
        var disponible = periodo.plusMonths(1).atDay(1);
        return new ReciboEmitido(numero, periodo, BigDecimal.ZERO, new BigDecimal("5.00"), BigDecimal.ZERO,
            new BigDecimal("5.00"), disponible, disponible.plusDays(7), "DOMESTICA", 1);
    }

    private static void publicar(String... recibos) {
        respuesta.set(new Respuesta(200, "application/json", """
            {"codigoSuministro":"000101","titular":"Persona ficticia","moneda":"PEN","recibos":[%s]}
            """.formatted(String.join(",", recibos))));
    }

    private static HttpServer iniciarEmpresa() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext(RUTA, intercambio -> responder(intercambio, respuesta.get()));
            servidor.createContext(RUTA.replace("000101", "000102"), intercambio -> responder(intercambio,
                new Respuesta(200, "application/json", "{\"codigoSuministro\":\"000102\",\"moneda\":\"PEN\",\"recibos\":[]}")));
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo iniciar la fuente de prueba.", error);
        }
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, Respuesta resultado) throws IOException {
        try (intercambio) {
            var cuerpo = resultado.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", resultado.tipo());
            intercambio.sendResponseHeaders(resultado.estado(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}
}
