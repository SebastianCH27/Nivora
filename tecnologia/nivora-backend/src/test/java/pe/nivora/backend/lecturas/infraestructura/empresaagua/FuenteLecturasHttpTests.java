package pe.nivora.backend.lecturas.infraestructura.empresaagua;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
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
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuenteLecturasHttpTests {

    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final String PRIMERA = lectura("391", "391", "920231");
    private static final String SEGUNDA = lectura("100391", "391", "920387");
    private static final String VALIDA = pagina("[" + PRIMERA + "," + SEGUNDA + "]", "100391", true);

    private final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private final AtomicInteger solicitudes = new AtomicInteger();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> consulta = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> aceptado = new AtomicReference<>();
    private HttpServer servidor;
    private ExecutorService ejecutor;
    private FuenteLecturasHttp fuente;

    @BeforeEach
    void iniciarServidorLocal() throws IOException {
        respuesta.set(new Respuesta(200, "application/json", VALIDA, 0));
        ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(ejecutor);
        servidor.createContext("/", this::responder);
        servidor.start();
        fuente = crearFuente(2000);
    }

    @AfterEach
    void cerrarServidorLocal() {
        servidor.stop(0);
        ejecutor.shutdownNow();
    }

    @Test
    void consultaYTraduceUnaPaginaCompleta() {
        var lote = fuente.consultar(CODIGO, "0", 2).orElseThrow();

        assertEquals("GET", metodo.get());
        assertEquals("/api/v1/suministros/13100390/lecturas", ruta.get());
        assertTrue(consulta.get().contains("despuesDe=0"));
        assertTrue(consulta.get().contains("limite=2"));
        assertTrue(aceptado.get().contains("application/json"));
        assertEquals("empresa-agua", lote.fuente());
        assertEquals(CODIGO, lote.suministro());
        assertEquals(2, lote.lecturas().size());
        var lectura = lote.lecturas().getFirst();
        assertEquals("391", lectura.idLectura());
        assertEquals("391", lectura.idMedidor());
        assertEquals(CODIGO, lectura.suministro());
        assertEquals("empresa-agua", lectura.fuente());
        assertEquals(920231, lectura.valorLitros());
        assertEquals(Instant.parse("2026-10-01T05:00:00.123456Z"), lectura.fechaMedicion());
        assertEquals("100391", lote.siguienteCursor());
        assertTrue(lote.hayMas());
    }

    @Test
    void continuaDesdeElCursorSolicitado() {
        respuesta.set(new Respuesta(200, "application/json",
            pagina("[" + lectura("200391", "391", "920574") + ","
                + lectura("300391", "391", "920730") + "]", "300391", true), 0));

        var lote = fuente.consultar(CODIGO, "100391", 2).orElseThrow();

        assertEquals("200391", lote.lecturas().getFirst().idLectura());
        assertEquals("300391", lote.siguienteCursor());
        assertTrue(consulta.get().contains("despuesDe=100391"));
    }

    @Test
    void distingueUnaPaginaVaciaDeUnSuministroInexistente() {
        respuesta.set(new Respuesta(200, "application/json", pagina("[]", "300391", false), 0));

        var lote = fuente.consultar(CODIGO, "300391", 2).orElseThrow();

        assertTrue(lote.lecturas().isEmpty());
        assertEquals("300391", lote.siguienteCursor());
        assertFalse(lote.hayMas());
    }

    @Test
    void devuelveVacioSoloSiLaAusenciaEstaConfirmada() {
        respuesta.set(new Respuesta(404, "application/problem+json",
            problema("Suministro no encontrado", "/api/v1/suministros/13100390/lecturas"), 0));

        assertTrue(fuente.consultar(CODIGO, "0", 2).isEmpty());
    }

    @Test
    void conservaLosCerosDelSuministro() {
        respuesta.set(new Respuesta(200, "application/json", VALIDA.replace("13100390", "00123"), 0));

        var lote = fuente.consultar(new CodigoSuministro("00123"), "0", 2).orElseThrow();

        assertEquals("00123", lote.suministro().valor());
        assertEquals("/api/v1/suministros/00123/lecturas", ruta.get());
    }

    @Test
    void conservaLosDigitosDeIdentificadoresGrandes() {
        respuesta.set(new Respuesta(200, "application/json",
            pagina("[" + lectura("9007199254740993", "9007199254740995", "1") + "]",
                "9007199254740993", false), 0));

        var lote = fuente.consultar(CODIGO, "9007199254740992", 2).orElseThrow();

        assertEquals("9007199254740993", lote.lecturas().getFirst().idLectura());
        assertEquals("9007199254740995", lote.lecturas().getFirst().idMedidor());
        assertEquals("9007199254740993", lote.siguienteCursor());
    }

    @Test
    void admiteUnContadorEnCeroDeOtroMedidor() {
        respuesta.set(new Respuesta(200, "application/json",
            pagina("[" + PRIMERA + "," + lectura("100391", "999", "0") + "]", "100391", true), 0));

        var lote = fuente.consultar(CODIGO, "0", 2).orElseThrow();

        assertEquals("999", lote.lecturas().getLast().idMedidor());
        assertEquals(0, lote.lecturas().getLast().valorLitros());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paginasInvalidas")
    void rechazaUnaPaginaIncompatibleCompleta(String escenario, String cuerpo) {
        respuesta.set(new Respuesta(200, "application/json", cuerpo, 0));

        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "0", 2), escenario);
        assertEquals(1, solicitudes.get());
    }

    static Stream<Arguments> paginasInvalidas() {
        return Stream.of(
            Arguments.of("fuente diferente", VALIDA.replace("empresa-agua", "otra-fuente")),
            Arguments.of("suministro diferente", VALIDA.replace("13100390", "99999999")),
            Arguments.of("tipo de medicion diferente", VALIDA.replace("ACUMULADA", "INCREMENTO")),
            Arguments.of("unidad diferente", VALIDA.replace("\"L\"", "\"m3\"")),
            Arguments.of("lecturas nulas", pagina("null", "0", false)),
            Arguments.of("lecturas como objeto", pagina("{}", "0", false)),
            Arguments.of("demasiadas lecturas", pagina("[" + PRIMERA + "," + SEGUNDA + ","
                + lectura("200391", "391", "920574") + "]", "200391", true)),
            Arguments.of("identificadores duplicados", pagina("[" + PRIMERA + "," + PRIMERA + "]", "391", true)),
            Arguments.of("identificadores desordenados", pagina("[" + SEGUNDA + "," + PRIMERA + "]", "391", true)),
            Arguments.of("identificador de lectura cero", VALIDA.replace("\"idLectura\":\"391\"", "\"idLectura\":\"0\"")),
            Arguments.of("identificador numerico en lugar de texto", VALIDA.replace("\"idLectura\":\"391\"", "\"idLectura\":391")),
            Arguments.of("identificador fuera de rango", VALIDA.replace("\"idLectura\":\"391\"", "\"idLectura\":\"9223372036854775808\"")),
            Arguments.of("medidor sin identificador", VALIDA.replace("\"idMedidor\":\"391\"", "\"idMedidor\":\"\"")),
            Arguments.of("medidor numerico en lugar de texto", VALIDA.replace("\"idMedidor\":\"391\"", "\"idMedidor\":391")),
            Arguments.of("medidor no decimal", VALIDA.replace("\"idMedidor\":\"391\"", "\"idMedidor\":\"ABC\"")),
            Arguments.of("fecha invalida", VALIDA.replace("2026-10-01T05:00:00.123456Z", "fecha-invalida")),
            Arguments.of("fecha sin zona", VALIDA.replace("2026-10-01T05:00:00.123456Z", "2026-10-01T05:00:00")),
            Arguments.of("fecha con otro formato de zona", VALIDA.replace("2026-10-01T05:00:00.123456Z", "2026-10-01T00:00:00-05:00")),
            Arguments.of("contador negativo", VALIDA.replace("920231", "-1")),
            Arguments.of("contador fraccionario", VALIDA.replace("920231", "1.5")),
            Arguments.of("contador textual", VALIDA.replace("920231", "\"920231\"")),
            Arguments.of("contador fuera de rango", VALIDA.replace("920231", "9223372036854775808")),
            Arguments.of("contador nulo", VALIDA.replace("920231", "null")),
            Arguments.of("indicador de paginas textual", VALIDA.replace("\"hayMas\":true", "\"hayMas\":\"true\"")),
            Arguments.of("pagina vacia con mas resultados", pagina("[]", "0", true)),
            Arguments.of("cursor final incorrecto", VALIDA.replace("\"siguienteDespuesDe\":\"100391\"", "\"siguienteDespuesDe\":\"200391\"")),
            Arguments.of("cursor final numerico", VALIDA.replace("\"siguienteDespuesDe\":\"100391\"", "\"siguienteDespuesDe\":100391")),
            Arguments.of("cursor final retrocede", VALIDA.replace("\"siguienteDespuesDe\":\"100391\"", "\"siguienteDespuesDe\":\"0\"")),
            Arguments.of("pagina parcial anunciando mas resultados", pagina("[" + PRIMERA + "]", "391", true)),
            Arguments.of("elemento de lectura nulo", pagina("[null]", "0", false)),
            Arguments.of("elemento de lectura como lista", pagina("[[]]", "0", false))
        );
    }

    @Test
    void rechazaUnaLecturaAnteriorAlCursorSolicitado() {
        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "100391", 2));
    }

    @Test
    void unaPaginaVaciaNoPuedeAdelantarElCursor() {
        respuesta.set(new Respuesta(200, "application/json", pagina("[]", "500000", false), 0));

        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "100391", 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("respuestasHttpInvalidas")
    void unFalloHttpNoSeConvierteEnUnaPaginaVacia(
            String escenario, int estado, String tipo, String cuerpo) {
        respuesta.set(new Respuesta(estado, tipo, cuerpo, 0));

        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "0", 2), escenario);
        assertEquals(1, solicitudes.get());
    }

    static Stream<Arguments> respuestasHttpInvalidas() {
        return Stream.of(
            Arguments.of("404 generico", 404, "application/problem+json", problema("Not Found", "/otra-ruta")),
            Arguments.of("404 de otro suministro", 404, "application/problem+json",
                problema("Suministro no encontrado", "/api/v1/suministros/99999999/lecturas")),
            Arguments.of("servicio caido", 503, "application/problem+json", "{}"),
            Arguments.of("peticion rechazada", 400, "application/problem+json", "{}"),
            Arguments.of("sin autorizacion", 401, "application/problem+json", "{}"),
            Arguments.of("limite de peticiones", 429, "application/problem+json", "{}"),
            Arguments.of("redireccion", 302, "text/plain", ""),
            Arguments.of("sin contenido", 204, "application/json", ""),
            Arguments.of("JSON incorrecto", 200, "application/json", "{incompleto"),
            Arguments.of("documentos concatenados", 200, "application/json", VALIDA + " {}"),
            Arguments.of("tipo de contenido incorrecto", 200, "text/html", VALIDA),
            Arguments.of("cuerpo vacio", 200, "application/json", ""),
            Arguments.of("cuerpo demasiado grande", 200, "application/json", " ".repeat(512 * 1024 + 1))
        );
    }

    @ParameterizedTest
    @MethodSource("peticionesInvalidas")
    void rechazaPeticionesInvalidasAntesDeContactarConLaFuente(String cursor, int limite) {
        assertThrows(IllegalArgumentException.class, () -> fuente.consultar(CODIGO, cursor, limite));
        assertEquals(0, solicitudes.get());
    }

    static Stream<Arguments> peticionesInvalidas() {
        return Stream.of(Arguments.of(null, 2), Arguments.of("", 2), Arguments.of("-1", 2),
            Arguments.of("00", 2), Arguments.of("ABC", 2), Arguments.of("9223372036854775808", 2),
            Arguments.of("0", 0), Arguments.of("0", 501));
    }

    @Test
    void informaSiLaConexionEsRechazada() {
        servidor.stop(0);
        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "0", 2));
    }

    @Test
    void limitaLaEsperaDelCuerpoDeLaPagina() {
        fuente = crearFuente(100);
        respuesta.set(new Respuesta(200, "application/json", VALIDA, 1000));
        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> fuente.consultar(CODIGO, "0", 2));
    }

    private FuenteLecturasHttp crearFuente(int lecturaMs) {
        return new FuenteLecturasHttp(new ClienteEmpresaAgua(
            "http://127.0.0.1:" + servidor.getAddress().getPort(), 2000, lecturaMs,
            JsonMapper.builder().build()));
    }

    private static String lectura(String id, String medidor, String litros) {
        return "{\"idLectura\":\"" + id + "\",\"idMedidor\":\"" + medidor
            + "\",\"fechaMedicion\":\"2026-10-01T05:00:00.123456Z\",\"valorLitros\":" + litros + "}";
    }

    private static String pagina(String filas, String siguiente, boolean mas) {
        return "{\"fuente\":\"empresa-agua\",\"codigoSuministro\":\"13100390\","
            + "\"tipoMedicion\":\"ACUMULADA\",\"unidad\":\"L\",\"lecturas\":" + filas
            + ",\"siguienteDespuesDe\":\"" + siguiente + "\",\"hayMas\":" + mas + "}";
    }

    private static String problema(String titulo, String instancia) {
        return "{\"status\":404,\"title\":\"" + titulo + "\",\"instance\":\"" + instancia + "\"}";
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            solicitudes.incrementAndGet();
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            consulta.set(intercambio.getRequestURI().getRawQuery());
            aceptado.set(intercambio.getRequestHeaders().getFirst("Accept"));
            Respuesta datos = respuesta.get();
            intercambio.getResponseHeaders().set("Content-Type", datos.tipo());
            if (datos.estado() == 302) {
                intercambio.getResponseHeaders().set("Location", "/destino-incorrecto");
            }
            if (datos.estado() == 204) {
                intercambio.sendResponseHeaders(204, -1);
                return;
            }
            byte[] cuerpo = datos.cuerpo().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(datos.estado(), cuerpo.length);
            Thread.sleep(datos.demoraCuerpoMs());
            intercambio.getResponseBody().write(cuerpo);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            if (respuesta.get().demoraCuerpoMs() == 0) {
                throw ex;
            }
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo, long demoraCuerpoMs) {}
}
