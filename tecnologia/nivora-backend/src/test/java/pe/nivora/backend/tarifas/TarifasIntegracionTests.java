package pe.nivora.backend.tarifas;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TarifasIntegracionTests {
    private static final String FUENTE = "empresa-agua";
    private static final CodigoSuministro SUMINISTRO = new CodigoSuministro("000101");
    private static final String RUTA = "/api/v1/suministros/000101/tarifas";
    private static final Instant AHORA = Instant.parse("2026-11-11T23:05:00Z");
    private static final AtomicReference<Instant> reloj = new AtomicReference<>(AHORA);
    private static final String VERSION_1 = """
        {
          "codigoTarifa":"DOMESTICA","versionTarifa":1,"nombreTarifa":"Domestica",
          "cargoFijo":5.00,"vigenteDesde":"2026-10-01","vigenteHasta":null,
          "asignadaDesde":"2026-10-01","asignadaHasta":null,
          "aplicableDesde":"2026-10-01","aplicableHasta":null,
          "tramos":[{"desdeM3":0,"hastaM3":10,"precioPorM3":1.5000},
                    {"desdeM3":10,"hastaM3":null,"precioPorM3":2.2543}]
        }
        """;
    private static final String CERRADA = VERSION_1
        .replace("\"vigenteHasta\":null", "\"vigenteHasta\":\"2026-11-01\"")
        .replace("\"aplicableHasta\":null", "\"aplicableHasta\":\"2026-11-01\"");
    private static final String VERSION_2 = """
        {
          "codigoTarifa":"DOMESTICA","versionTarifa":2,"nombreTarifa":"Domestica revisada",
          "cargoFijo":6.00,"igvPorcentaje":18,"vigenteDesde":"2026-11-01","vigenteHasta":null,
          "asignadaDesde":"2026-11-01","asignadaHasta":null,
          "aplicableDesde":"2026-11-01","aplicableHasta":null,
          "tramos":[{"desdeM3":0,"hastaM3":null,"precioPorM3":2.2500,"precioAlcantarilladoPorM3":0}]
        }
        """;
    private static final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private static final AtomicReference<String> rutaConsultada = new AtomicReference<>();
    private static final HttpServer empresa = iniciarEmpresa();

    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IncorporarTarifas incorporar;
    @Autowired private RepositorioTarifas repositorio;
    private Connection esquema;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_tarifas;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base", () -> "http://127.0.0.1:" + empresa.getAddress().getPort());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {
        @Bean @Primary Clock relojTarifasPrueba() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zona) { return Clock.fixed(instant(), zona); }
                @Override public Instant instant() { return reloj.get(); }
            };
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        if (esquema == null) {
            esquema = datos.getConnection();
            new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
                new FileSystemResource("base-datos/10-tarifas.sql")).populate(esquema);
        }
        jdbc.execute("DELETE FROM tarifas_asignaciones");
        jdbc.execute("DELETE FROM tarifas_historiales");
        jdbc.execute("DELETE FROM tarifas_versiones");
        jdbc.execute("DELETE FROM suministros");
        jdbc.update("INSERT INTO suministros VALUES ('000101','Persona ficticia','2026-11-01 00:00:00')");
        reloj.set(AHORA);
        publicar(VERSION_1);
    }

    @AfterAll
    void cerrar() throws Exception {
        empresa.stop(0);
        if (esquema != null) esquema.close();
    }

    @Test
    void importaLaTarifaDelSuministroYRepetirlaConservaLaRevision() {
        assertTrue(repositorio.buscar(FUENTE, SUMINISTRO).isEmpty());
        var primera = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        assertEquals(RUTA, rutaConsultada.get());
        assertEquals(1L, primera.revision());
        assertEquals(primera, repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow());
        var tarifa = primera.historial().asignaciones().getFirst().tarifa();
        assertEquals(new BigDecimal("5.00"), tarifa.cargoFijo());
        assertEquals(new BigDecimal("2.2543"), tarifa.tramos().getLast().precioAguaPorM3());
        assertNull(tarifa.tramos().getFirst().precioAlcantarilladoPorM3());
        assertNull(tarifa.igvPorcentaje());

        reloj.set(AHORA.plusSeconds(60));
        var repetida = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        assertEquals(1L, repetida.revision());
        assertEquals(reloj.get(), repetida.consultadoEn());
        assertEquals(1L, cantidad("tarifas_historiales"));
        assertEquals(1L, cantidad("tarifas_versiones"));
    }

    @Test
    void unCambioDeVigenciaConservaLasVersionesYSeleccionaPorIntervalo() {
        incorporar.ejecutar(SUMINISTRO).orElseThrow();
        reloj.set(AHORA.plusSeconds(60));
        publicar(CERRADA + "," + VERSION_2);
        assertEquals(2L, incorporar.ejecutar(SUMINISTRO).orElseThrow().revision());

        var historial = repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow().historial();
        var octubre = LocalDate.parse("2026-10-01");
        var noviembre = octubre.plusMonths(1);
        var diciembre = noviembre.plusMonths(1);
        assertEquals(1, historial.aplicable(octubre, noviembre).orElseThrow().tarifa().version());
        var nueva = historial.aplicable(noviembre, diciembre).orElseThrow().tarifa();
        assertEquals(2, nueva.version());
        assertEquals(new BigDecimal("18.0000"), nueva.igvPorcentaje());
        assertEquals(new BigDecimal("0.0000"), nueva.tramos().getFirst().precioAlcantarilladoPorM3());
        assertTrue(historial.aplicable(octubre, diciembre).isEmpty());
        assertEquals(3L, cantidad("tarifas_asignaciones"));
        assertEquals(2L, cantidad("tarifas_versiones"));
        assertNull(jdbc.queryForObject("SELECT vigente_hasta FROM tarifas_asignaciones WHERE revision = 1",
            LocalDate.class));
    }

    @Test
    void fallaElCambioDeImportesEnUnaVersionExistenteYRevierteElLoteCompleto() {
        var original = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        reloj.set(AHORA.plusSeconds(60));
        publicar(CERRADA.replace("5.00", "9.00") + "," + VERSION_2.replace("DOMESTICA", "A-NUEVA"));
        assertThrows(IllegalStateException.class, () -> incorporar.ejecutar(SUMINISTRO));
        assertEquals(original, repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow());
        assertEquals(1L, cantidad("tarifas_historiales"));
        assertEquals(1L, cantidad("tarifas_versiones"));
    }

    @Test
    void laAusenciaConfirmadaYLosFallosHttpConservanElHistorial() {
        var original = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        respuesta.set(new Respuesta(404, "application/problem+json", """
            {"status":404,"title":"Suministro no encontrado","instance":"%s"}
            """.formatted(RUTA)));
        assertTrue(incorporar.ejecutar(SUMINISTRO).isEmpty());
        respuesta.set(new Respuesta(404, "application/problem+json", """
            {"status":404,"title":"Not Found","instance":"/ruta-distinta"}
            """));
        assertThrows(IllegalStateException.class, () -> incorporar.ejecutar(SUMINISTRO));
        respuesta.set(new Respuesta(503, "application/problem+json", "{}"));
        assertThrows(IllegalStateException.class, () -> incorporar.ejecutar(SUMINISTRO));
        assertEquals(original, repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"suministro", "fechas", "precision", "superposicion"})
    void rechazaUnHistorialInconsistente(String caso) {
        var original = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        String cuerpo = switch (caso) {
            case "suministro" -> cuerpo(VERSION_1).replace("000101", "000102");
            case "fechas" -> cuerpo(VERSION_1.replace(
                "\"aplicableDesde\":\"2026-10-01\"", "\"aplicableDesde\":\"2026-10-02\""));
            case "precision" -> cuerpo(VERSION_1.replace("2.2543", "2.25430000000000001"));
            case "superposicion" -> cuerpo(VERSION_1 + "," + VERSION_2);
            default -> throw new AssertionError(caso);
        };
        respuesta.set(new Respuesta(200, "application/json", cuerpo));
        assertThrows(IllegalStateException.class, () -> incorporar.ejecutar(SUMINISTRO));
        assertEquals(original, repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow());
    }

    @Test
    void conservaUnaRespuestaVaciaYDescartaUnaObservacionAnterior() {
        incorporar.ejecutar(SUMINISTRO).orElseThrow();
        reloj.set(AHORA.plusSeconds(120));
        publicar("");
        var vacia = incorporar.ejecutar(SUMINISTRO).orElseThrow();
        assertEquals(2L, vacia.revision());
        assertTrue(vacia.historial().asignaciones().isEmpty());
        assertEquals(vacia, repositorio.buscar(FUENTE, SUMINISTRO).orElseThrow());

        reloj.set(AHORA.plusSeconds(60));
        publicar(VERSION_1);
        assertEquals(vacia, incorporar.ejecutar(SUMINISTRO).orElseThrow());
        assertEquals(1L, cantidad("tarifas_asignaciones"));
    }

    private long cantidad(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
    }

    private static void publicar(String asignaciones) {
        respuesta.set(new Respuesta(200, "application/json", cuerpo(asignaciones)));
    }

    private static String cuerpo(String asignaciones) {
        return """
            {"fuente":"empresa-agua","codigoSuministro":"000101","moneda":"PEN",
             "zonaFechas":"America/Lima","asignaciones":[%s]}
            """.formatted(asignaciones);
    }

    private static HttpServer iniciarEmpresa() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext(RUTA, intercambio -> {
                try (intercambio) {
                    rutaConsultada.set(intercambio.getRequestURI().getPath());
                    var actual = respuesta.get();
                    byte[] bytes = actual.cuerpo().getBytes(StandardCharsets.UTF_8);
                    intercambio.getResponseHeaders().set("Content-Type", actual.tipo());
                    intercambio.sendResponseHeaders(actual.estado(), bytes.length);
                    intercambio.getResponseBody().write(bytes);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo iniciar el servidor HTTP de pruebas.", error);
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}
}
