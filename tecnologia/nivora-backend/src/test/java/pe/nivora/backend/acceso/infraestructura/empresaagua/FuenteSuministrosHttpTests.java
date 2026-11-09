package pe.nivora.backend.acceso.infraestructura.empresaagua;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Usa un servidor HTTP local para ejercitar tambien el transporte del adaptador. */
class FuenteSuministrosHttpTests {

    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final String SUMINISTRO =
        "{\"codigoSuministro\":\"13100390\",\"titular\":\"María Pérez Torres\"}";

    private final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private final AtomicInteger solicitudes = new AtomicInteger();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> aceptado = new AtomicReference<>();

    private HttpServer servidor;
    private ExecutorService ejecutor;
    private FuenteSuministrosHttp fuente;

    @BeforeEach
    void iniciarFuenteHttp() throws IOException {
        respuesta.set(new Respuesta(200, "application/json", SUMINISTRO, 0, 0, null));
        ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(ejecutor);
        servidor.createContext("/", this::responder);
        servidor.start();
        fuente = crearFuente(2000);
    }

    @AfterEach
    void cerrarFuenteHttp() {
        servidor.stop(0);
        ejecutor.shutdownNow();
    }

    @Test
    void consultaElCodigoYConservaElTitularUtf8() {
        var suministro = fuente.consultar(CODIGO).orElseThrow();

        assertEquals(CODIGO, suministro.codigo());
        assertEquals("María Pérez Torres", suministro.titular());
        assertEquals("GET", metodo.get());
        assertEquals("/api/v1/suministros/13100390", ruta.get());
        assertTrue(aceptado.get().contains("application/json"));
        assertEquals(1, solicitudes.get());
    }

    @Test
    void conservaLosCerosInicialesDelCodigo() {
        respuesta.set(new Respuesta(200, "application/json; charset=UTF-8",
            "{\"codigoSuministro\":\"00123\",\"titular\":\"Titular de prueba\"}", 0, 0, null));

        var suministro = fuente.consultar(new CodigoSuministro("00123")).orElseThrow();

        assertEquals("00123", suministro.codigo().valor());
        assertEquals("/api/v1/suministros/00123", ruta.get());
    }

    @Test
    void devuelveVacioSoloAnteAusenciaConfirmadaPorElContrato() {
        respuesta.set(new Respuesta(404, "application/problem+json",
            problema("Suministro no encontrado", "/api/v1/suministros/13100390"),
            0, 0, null));

        assertTrue(fuente.consultar(CODIGO).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("respuestasNoUtilizables")
    void noConvierteErroresNiRespuestasInvalidasEnAusencia(
            String escenario, int estado, String tipo, String cuerpo) {
        respuesta.set(new Respuesta(estado, tipo, cuerpo, 0, 0, null));

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> fuente.consultar(CODIGO), escenario);
    }

    static Stream<Arguments> respuestasNoUtilizables() {
        return Stream.of(
            Arguments.of("404 de una ruta incorrecta", 404, "application/problem+json",
                problema("Not Found", "/otra-ruta")),
            Arguments.of("404 correspondiente a otro suministro", 404,
                "application/problem+json",
                problema("Suministro no encontrado", "/api/v1/suministros/99999999")),
            Arguments.of("404 sin contrato JSON", 404, "text/html", "<h1>Not Found</h1>"),
            Arguments.of("404 sin estado en el problema", 404, "application/problem+json",
                "{\"title\":\"Suministro no encontrado\","
                + "\"instance\":\"/api/v1/suministros/13100390\"}"),
            Arguments.of("estado con desbordamiento numerico", 404, "application/problem+json",
                problema("Suministro no encontrado", "/api/v1/suministros/13100390")
                    .replace("404", "4294967700")),
            Arguments.of("servicio temporalmente caido", 503, "application/problem+json", "{}"),
            Arguments.of("peticion rechazada", 400, "application/problem+json", "{}"),
            Arguments.of("credencial del proveedor requerida", 401, "application/json", "{}"),
            Arguments.of("limite de consultas", 429, "application/json", "{}"),
            Arguments.of("respuesta sin contenido", 204, "application/json", ""),
            Arguments.of("JSON malformado", 200, "application/json", "{incompleto"),
            Arguments.of("dos documentos JSON concatenados", 200, "application/json",
                SUMINISTRO + " {}"),
            Arguments.of("datos despues del JSON", 200, "application/json", SUMINISTRO + " basura"),
            Arguments.of("codigo de otro suministro", 200, "application/json",
                SUMINISTRO.replace("13100390", "99999999")),
            Arguments.of("codigo numerico sin conservar el formato", 200, "application/json",
                "{\"codigoSuministro\":13100390,\"titular\":\"Titular\"}"),
            Arguments.of("titular ausente", 200, "application/json",
                "{\"codigoSuministro\":\"13100390\"}"),
            Arguments.of("titular vacio", 200, "application/json",
                "{\"codigoSuministro\":\"13100390\",\"titular\":\"   \"}"),
            Arguments.of("tipo de contenido incorrecto", 200, "text/plain", SUMINISTRO),
            Arguments.of("tipo de contenido ausente", 200, null, SUMINISTRO),
            Arguments.of("cuerpo vacio", 200, "application/json", ""),
            Arguments.of("lista en lugar de objeto", 200, "application/json", "[]"),
            Arguments.of("respuesta demasiado grande", 200, "application/json",
                " ".repeat(64 * 1024 + 1))
        );
    }

    @Test
    void noSigueRedireccionesDelProveedor() {
        respuesta.set(new Respuesta(302, "text/plain", "", 0, 0,
            urlBase() + "/destino-incorrecto"));

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> fuente.consultar(CODIGO));
        assertEquals(1, solicitudes.get());
    }

    @Test
    void informaIndisponibilidadSiNoPuedeConectarse() {
        servidor.stop(0);

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> fuente.consultar(CODIGO));
    }

    @Test
    void limitaLaEsperaDeLasCabecerasHttp() {
        fuente = crearFuente(100);
        respuesta.set(new Respuesta(200, "application/json", SUMINISTRO, 1000, 0, null));

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> fuente.consultar(CODIGO));
        assertEquals(1, solicitudes.get());
    }

    @Test
    void limitaLaEsperaDelCuerpoAunqueLasCabecerasYaLlegaron() {
        fuente = crearFuente(100);
        respuesta.set(new Respuesta(200, "application/json", SUMINISTRO, 0, 1000, null));

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> fuente.consultar(CODIGO));
        assertEquals(1, solicitudes.get());
    }

    private FuenteSuministrosHttp crearFuente(int lecturaMs) {
        return new FuenteSuministrosHttp(urlBase(), 2000, lecturaMs, JsonMapper.builder().build());
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static String problema(String titulo, String instancia) {
        return "{\"status\":404,\"title\":\"" + titulo
            + "\",\"instance\":\"" + instancia + "\"}";
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            solicitudes.incrementAndGet();
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            aceptado.set(intercambio.getRequestHeaders().getFirst("Accept"));
            Respuesta datos = respuesta.get();
            Thread.sleep(datos.demoraCabecerasMs());

            if (datos.tipo() != null) {
                intercambio.getResponseHeaders().set("Content-Type", datos.tipo());
            }
            if (datos.ubicacion() != null) {
                intercambio.getResponseHeaders().set("Location", datos.ubicacion());
            }
            if (datos.estado() == 204) {
                intercambio.sendResponseHeaders(204, -1);
                return;
            }

            byte[] cuerpo = datos.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(datos.estado(), cuerpo.length);
            Thread.sleep(datos.demoraCuerpoMs());
            intercambio.getResponseBody().write(cuerpo);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            // El adaptador cierra la conexion al vencer el tiempo de lectura.
            if (respuesta.get().demoraCuerpoMs() == 0
                    && respuesta.get().demoraCabecerasMs() == 0) {
                throw ex;
            }
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo,
                             long demoraCabecerasMs, long demoraCuerpoMs, String ubicacion) {
    }
}
