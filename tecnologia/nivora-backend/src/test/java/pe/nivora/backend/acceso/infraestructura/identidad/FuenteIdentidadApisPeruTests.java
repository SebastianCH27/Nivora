package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
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

    private final java.util.concurrent.atomic.AtomicLong demoraMs = new java.util.concurrent.atomic.AtomicLong();
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
    void consultaLaRutaConElTokenRequeridoPorElProveedorYConservaLosCerosIniciales() {
        var identidad = fuente.consultar(DNI);

        assertEquals(DNI, identidad.dni());
        assertEquals("María Persona Ejemplo", identidad.nombreCompleto());
        assertEquals(new Peticion("GET", "/api/v1/dni/00000001", "token=" + TOKEN, null),
            peticion.get());
        assertFalse(identidad.toString().contains("María"));
        assertFalse(identidad.toString().contains(DNI.valor()));
        assertFalse(DNI.toString().contains(DNI.valor()));
    }

    @Test
    void codificaElTokenParaConservarSignosReservadosEnLaConsulta() {
        String token = "ficticio+con/barras==";
        fuente = new FuenteIdentidadApisPeru(URI.create("http://127.0.0.1:"
            + servidor.getAddress().getPort()), token, JsonMapper.builder().build());

        assertEquals(DNI, fuente.consultar(DNI).dni());
        assertEquals("token=ficticio%2Bcon%2Fbarras%3D%3D", peticion.get().consulta());
        assertEquals("token=" + token,
            URLDecoder.decode(peticion.get().consulta(), StandardCharsets.UTF_8));
        assertNull(peticion.get().autorizacion());
    }

    @Test
    void unProveedorQueExigeTokenEnLaConsultaPermiteConfirmarLaIdentidad() throws Exception {
        var proveedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            proveedor.createContext("/api/v1/dni/", intercambio -> {
                try (intercambio) {
                    // Una cabecera Bearer por sí sola no satisface este contrato.
                    boolean autorizado = ("token=" + TOKEN).equals(intercambio.getRequestURI().getRawQuery());
                    byte[] cuerpo = (autorizado ? PERSONA : "{\"message\":\"Token not found\"}")
                        .getBytes(StandardCharsets.UTF_8);
                    intercambio.getResponseHeaders().set("Content-Type", "application/json");
                    intercambio.sendResponseHeaders(autorizado ? 200 : 401, cuerpo.length);
                    intercambio.getResponseBody().write(cuerpo);
                }
            });
            proveedor.start();
            var adaptador = new FuenteIdentidadApisPeru(URI.create("http://127.0.0.1:"
                + proveedor.getAddress().getPort()), TOKEN, JsonMapper.builder().build());
            assertEquals(DNI, adaptador.consultar(DNI).dni());
        } finally {
            proveedor.stop(0);
        }
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
            Arguments.of(403, "application/json", PERSONA, Motivo.CREDENCIAL_RECHAZADA),
            Arguments.of(500, "application/json", "{}", Motivo.FUENTE_NO_DISPONIBLE),
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

    @Test
    void unaConexionInterrumpidaSeClasificaSinExponerDatos() throws Exception {
        servidor.stop(0);
        var error = assertThrows(FuenteIdentidadNoDisponibleException.class, () -> fuente.consultar(DNI));
        assertEquals(Motivo.FUENTE_NO_DISPONIBLE, error.motivo());
        assertFalse(error.getMessage().contains(TOKEN));
    }

    @Test
    void limitaLaEsperaDeUnaFuenteQueNoResponde() {
        demoraMs.set(11000);
        var inicio = System.nanoTime();
        var error = assertThrows(FuenteIdentidadNoDisponibleException.class, () -> fuente.consultar(DNI));
        assertEquals(Motivo.FUENTE_NO_DISPONIBLE, error.motivo());
        org.junit.jupiter.api.Assertions.assertTrue(java.time.Duration.ofNanos(System.nanoTime() - inicio).toSeconds() < 20);
        assertFalse(error.getMessage().contains(TOKEN));
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            if (demoraMs.get() > 0) try { Thread.sleep(demoraMs.get()); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
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
