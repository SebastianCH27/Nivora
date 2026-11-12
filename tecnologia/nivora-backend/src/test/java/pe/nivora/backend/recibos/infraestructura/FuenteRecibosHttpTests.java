package pe.nivora.backend.recibos.infraestructura;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuenteRecibosHttpTests {
    private static final CodigoSuministro SUMINISTRO = new CodigoSuministro("000101");
    private static final String RUTA = "/api/v1/suministros/000101/recibos";
    private static final Instant DISPONIBLE = Instant.parse("2026-11-01T05:00:00Z");
    private static final String OCTUBRE = """
        {"numeroRecibo":"REC-000101-202610","periodo":"2026-10-01","consumoLitros":5637.125,
         "cargoFijo":5.00,"importeConsumo":8.46,"importeTotal":13.46,
         "fechaDisponibilidad":"2026-11-01","fechaVencimiento":"2026-11-09",
         "codigoTarifa":"DOMESTICA","versionTarifa":1}
        """;
    private static final String SEPTIEMBRE = OCTUBRE
        .replace("202610", "202609").replace("2026-10-01", "2026-09-01")
        .replace("2026-11-01", "2026-10-01").replace("2026-11-09", "2026-10-09");

    private final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private final AtomicReference<String> peticion = new AtomicReference<>();
    private final AtomicInteger llamadas = new AtomicInteger();
    private HttpServer servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            try (intercambio) {
                llamadas.incrementAndGet();
                peticion.set(intercambio.getRequestMethod() + " " + intercambio.getRequestURI());
                var resultado = respuesta.get();
                var cuerpo = resultado.cuerpo().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", resultado.tipo());
                intercambio.sendResponseHeaders(resultado.estado(), cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void cerrar() {
        servidor.stop(0);
    }

    @Test
    void consultaElSuministroConCerosYConservaImporteVencimientoYTarifaEmitidos() {
        respuesta.set(json(envolver(OCTUBRE, SEPTIEMBRE)));
        var resultado = fuente(DISPONIBLE).consultar(SUMINISTRO, 2).orElseThrow();
        assertEquals("GET " + RUTA + "?limite=2", peticion.get());
        assertEquals(SUMINISTRO, resultado.suministro());
        assertEquals(List.of(YearMonth.of(2026, 10), YearMonth.of(2026, 9)),
            resultado.recibos().stream().map(r -> r.periodo()).toList());
        var reciente = resultado.recibos().getFirst();
        assertEquals("REC-000101-202610", reciente.numeroRecibo());
        assertEquals(new BigDecimal("5637.125"), reciente.consumoLitros());
        assertEquals(new BigDecimal("13.46"), reciente.importeTotal());
        assertEquals(LocalDate.parse("2026-11-01"), reciente.fechaDisponibilidad());
        assertEquals(LocalDate.parse("2026-11-09"), reciente.fechaVencimiento());
        assertEquals("DOMESTICA", reciente.codigoTarifa());
        assertEquals(1, reciente.versionTarifa());
    }

    @Test
    void interpretaLaDisponibilidadSegunLaMedianocheDeLima() {
        respuesta.set(json(envolver(OCTUBRE)));
        assertThrows(FuenteRecibosNoDisponibleException.class,
            () -> fuente(DISPONIBLE.minusNanos(1)).consultar(SUMINISTRO, 1));
        assertEquals(1, fuente(DISPONIBLE).consultar(SUMINISTRO, 1).orElseThrow().recibos().size());
    }

    @Test
    void distingueUnSuministroAusenteDeUnHistorialVacio() {
        var fuente = fuente(DISPONIBLE);
        respuesta.set(json(envolver()));
        assertTrue(fuente.consultar(SUMINISTRO, 60).orElseThrow().recibos().isEmpty());
        respuesta.set(problema404(RUTA));
        assertTrue(fuente.consultar(SUMINISTRO, 1).isEmpty());
        respuesta.set(problema404("/api/v1/suministros/000102/recibos"));
        assertThrows(FuenteRecibosNoDisponibleException.class, () -> fuente.consultar(SUMINISTRO, 1));
    }

    @Test
    void validaElLimiteAntesDeConsultarYRechazaExcesoDeFilas() {
        var fuente = fuente(DISPONIBLE);
        assertThrows(IllegalArgumentException.class, () -> fuente.consultar(SUMINISTRO, 0));
        assertThrows(IllegalArgumentException.class, () -> fuente.consultar(SUMINISTRO, 61));
        assertEquals(0, llamadas.get());
        respuesta.set(json(envolver(OCTUBRE, SEPTIEMBRE)));
        assertThrows(FuenteRecibosNoDisponibleException.class, () -> fuente.consultar(SUMINISTRO, 1));
    }

    @ParameterizedTest
    @MethodSource("respuestasInvalidas")
    void reportaFalloDeLaFuenteAnteUnaRespuestaInconsistente(Respuesta resultado) {
        respuesta.set(resultado);
        var fuente = fuente(DISPONIBLE);
        assertThrows(FuenteRecibosNoDisponibleException.class, () -> fuente.consultar(SUMINISTRO, 12));
    }

    private static Stream<Respuesta> respuestasInvalidas() {
        return Stream.of(
            new Respuesta(503, "application/problem+json", "{}"),
            new Respuesta(200, "text/plain", envolver(OCTUBRE)),
            json(envolver(OCTUBRE) + "{}"),
            json(envolver(OCTUBRE).replace("000101", "000102")),
            json(envolver(OCTUBRE).replace("PEN", "USD")),
            json(envolver(OCTUBRE.replace("13.46", "13.47"))),
            json(envolver(OCTUBRE.replace("13.46", "13.460000000000000001"))),
            json(envolver(OCTUBRE.replace("5637.125", "-1"))),
            json(envolver(OCTUBRE.replace("2026-10-01", "2026-10-02"))),
            json(envolver(OCTUBRE.replace("2026-11-09", "2026-10-31"))),
            json(envolver(OCTUBRE.replace("\"versionTarifa\":1", "\"versionTarifa\":2147483648"))),
            json(envolver(SEPTIEMBRE, OCTUBRE)),
            json(envolver(OCTUBRE, OCTUBRE.replace("REC-000101-202610", "OTRO-NUMERO"))),
            json(envolver(OCTUBRE, SEPTIEMBRE.replace("202609", "202610")))
        );
    }

    private FuenteRecibosHttp fuente(Instant instante) {
        var url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return new FuenteRecibosHttp(new ClienteEmpresaAgua(url, 1000, 2000, JsonMapper.builder().build()),
            Clock.fixed(instante, ZoneOffset.UTC));
    }

    private static String envolver(String... recibos) {
        return """
            {"codigoSuministro":"000101","titular":"Titular ficticio","moneda":"PEN","recibos":[%s]}
            """.formatted(String.join(",", recibos));
    }

    private static Respuesta json(String cuerpo) {
        return new Respuesta(200, "application/json", cuerpo);
    }

    private static Respuesta problema404(String ruta) {
        var cuerpo = """
            {"status":404,"title":"Suministro no encontrado","instance":"%s"}
            """.formatted(ruta);
        return new Respuesta(404, "application/problem+json", cuerpo);
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}
}
