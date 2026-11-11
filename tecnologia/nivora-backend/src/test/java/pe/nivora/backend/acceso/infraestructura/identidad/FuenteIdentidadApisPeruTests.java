package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException.Motivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuenteIdentidadApisPeruTests {

    private static final Dni DNI = new Dni("00000001");
    private static final String TOKEN = "token-ficticio.de.prueba";
    private static final String PERSONA = """
        {"dni":"00000001","nombres":"María","apellidoPaterno":"Persona",
         "apellidoMaterno":"Ejemplo"}
        """;

    private final AtomicReference<Respuesta> respuesta = new AtomicReference<>();
    private final AtomicReference<Peticion> peticion = new AtomicReference<>();
    private final AtomicInteger peticiones = new AtomicInteger();

    private HttpServer servidor;
    private FuenteIdentidadApisPeru fuente;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        respuesta.set(new Respuesta(200, "application/json; charset=utf-8", PERSONA));
        URI origen = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        fuente = new FuenteIdentidadApisPeru(origen, TOKEN, JsonMapper.builder().build());
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    void consultaLaRutaCompletaConBearerYConservaLosCerosIniciales() {
        var identidad = fuente.consultar(DNI);

        assertEquals(DNI, identidad.dni());
        assertEquals("María Persona Ejemplo", identidad.nombreCompleto());
        assertEquals(new Peticion("GET", "/api/v1/dni/00000001", null, "Bearer " + TOKEN),
            peticion.get());
        assertFalse(identidad.toString().contains("María"));
        assertFalse(identidad.toString().contains(DNI.valor()));
        assertFalse(DNI.toString().contains(DNI.valor()));
    }

    @Test
    void aceptaUnaRespuestaConUnSoloApellido() {
        respuesta.set(new Respuesta(200, "application/json",
            "{\"dni\":\"00000001\",\"nombres\":\"María\",\"apellidoPaterno\":\"Persona\"}"));

        assertEquals("María Persona", fuente.consultar(DNI).nombreCompleto());
    }

    @ParameterizedTest
    @MethodSource("respuestasFallidas")
    void informaUnaConsultaNoConfirmadaSinExponerElCuerpo(
            int estado, String tipo, String cuerpo, Motivo motivo) {
        respuesta.set(new Respuesta(estado, tipo, cuerpo));

        var error = assertThrows(FuenteIdentidadNoDisponibleException.class,
            () -> fuente.consultar(DNI));
        assertEquals(motivo, error.motivo());
        assertFalse(error.getMessage().contains(DNI.valor()));
        assertFalse(error.getMessage().contains(TOKEN));
        assertFalse(error.getMessage().contains("María"));
        assertNull(error.getCause());
    }

    static Stream<Arguments> respuestasFallidas() {
        return Stream.of(
            Arguments.of(401, "application/json", PERSONA, Motivo.CREDENCIAL_RECHAZADA),
            Arguments.of(429, "application/json", "{}", Motivo.LIMITE_CONSULTAS),
            Arguments.of(404, "text/html", "<h1>Not Found</h1>", Motivo.RESULTADO_NO_CONCLUYENTE),
            Arguments.of(503, "application/json", "{}", Motivo.FUENTE_NO_DISPONIBLE),
            Arguments.of(200, "application/json", "{\"success\":false,\"message\":\"No encontrado\"}",
                Motivo.RESULTADO_NO_CONCLUYENTE),
            Arguments.of(200, "application/json", PERSONA.replace("00000001", "00000002"),
                Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", PERSONA.replace("María", "   "),
                Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", PERSONA.replace("\"00000001\"", "1"),
                Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "text/plain", PERSONA, Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", "{incompleto", Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", PERSONA + " {}", Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", PERSONA.replace("\"María\"", "\"María\",\"nombres\":\"Otra\""),
                Motivo.RESPUESTA_INCOMPATIBLE),
            Arguments.of(200, "application/json", " ".repeat(16 * 1024 + 1),
                Motivo.RESPUESTA_INCOMPATIBLE)
        );
    }

    @Test
    void noSigueRedireccionesQuePodrianEnviarLaCredencialAOtraRuta() {
        respuesta.set(new Respuesta(302, "text/plain", ""));

        var error = assertThrows(FuenteIdentidadNoDisponibleException.class,
            () -> fuente.consultar(DNI));
        assertEquals(Motivo.FUENTE_NO_DISPONIBLE, error.motivo());
        assertEquals(1, peticiones.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "123456789", " 00000001", "0000000A", "１２３４５６７８"})
    void rechazaDocumentosQueNoTienenOchoDigitosAscii(String valor) {
        assertThrows(IllegalArgumentException.class, () -> new Dni(valor));
        assertEquals(0, peticiones.get());
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            peticiones.incrementAndGet();
            peticion.set(new Peticion(intercambio.getRequestMethod(),
                intercambio.getRequestURI().getPath(), intercambio.getRequestURI().getRawQuery(),
                intercambio.getRequestHeaders().getFirst("Authorization")));
            Respuesta datos = respuesta.get();
            intercambio.getResponseHeaders().set("Content-Type", datos.tipo());
            if (datos.estado() == 302) {
                intercambio.getResponseHeaders().set("Location", "/otra-ruta");
            }
            byte[] cuerpo = datos.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(datos.estado(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}

    private record Peticion(String metodo, String ruta, String consulta, String autorizacion) {}
}
