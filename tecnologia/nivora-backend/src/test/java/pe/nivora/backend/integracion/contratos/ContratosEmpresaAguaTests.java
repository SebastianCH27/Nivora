package pe.nivora.backend.integracion.contratos;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.infraestructura.empresaagua.FuenteSuministrosHttp;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.infraestructura.ContextoLecturasJson;
import pe.nivora.backend.lecturas.infraestructura.empresaagua.FuenteLecturasConCorteHttp;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;
import pe.nivora.backend.recibos.infraestructura.FuenteRecibosHttp;
import pe.nivora.backend.tarifas.dominio.FuenteTarifasNoDisponibleException;
import pe.nivora.backend.tarifas.infraestructura.FuenteTarifasHttp;
import static org.junit.jupiter.api.Assertions.*;

class ContratosEmpresaAguaTests {
    private static final CodigoSuministro CODIGO = new CodigoSuministro("000101");
    private static final String BASE = "/api/v1/suministros/000101";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneOffset.UTC);
    private final JsonMapper json = new JsonMapper();
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private HttpServer servidor;
    private ExecutorService hilos;
    private ClienteEmpresaAgua cliente;
    private JsonNode datos;
    private Punto alterar;
    private int estado;
    private String contenido;
    private String cuerpo;
    private long demora;

    enum Punto {
        SUMINISTRO(BASE, "suministro", FuenteSuministrosNoDisponibleException.class),
        INVENTARIO(BASE + "/medidores", "inventario", FuenteLecturasNoDisponibleException.class),
        CORTE("/api/v1/lecturas/corte", "corte", FuenteLecturasNoDisponibleException.class),
        LECTURAS(BASE + "/lecturas", "pagina", FuenteLecturasNoDisponibleException.class),
        TARIFAS(BASE + "/tarifas", "tarifas", FuenteTarifasNoDisponibleException.class),
        RECIBOS(BASE + "/recibos", "recibos", FuenteRecibosNoDisponibleException.class);
        final String ruta, seccion;
        final Class<? extends RuntimeException> error;
        Punto(String ruta, String seccion, Class<? extends RuntimeException> error) {
            this.ruta = ruta; this.seccion = seccion; this.error = error;
        }
    }

    @BeforeEach void iniciar() throws Exception {
        datos = json.readTree(getClass().getResourceAsStream("/contratos/empresa-agua.json"));
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hilos = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(hilos);
        servidor.createContext("/", q -> {
            try (q) {
                String ruta = q.getRequestURI().getPath();
                consultas.add(q.getRequestMethod() + " " + q.getRequestURI());
                assertTrue(q.getRequestHeaders().getFirst("Accept").contains("application/json"));
                var punto = Stream.of(Punto.values()).filter(p -> p.ruta.equals(ruta)).findFirst().orElseThrow();
                boolean cambio = punto == alterar;
                if (cambio && demora > 0) {
                    try { Thread.sleep(demora); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                byte[] bytes = (cambio ? cuerpo : json.writeValueAsString(datos.path(punto.seccion))).getBytes(StandardCharsets.UTF_8);
                q.getResponseHeaders().set("Content-Type", cambio ? contenido : "application/json");
                if (cambio && estado == 302) q.getResponseHeaders().set("Location", "/no-se-debe-seguir");
                q.sendResponseHeaders(cambio ? estado : 200, bytes.length);
                try { q.getResponseBody().write(bytes); } catch (java.io.IOException ignorado) { }
            }
        });
        servidor.start();
        cliente = new ClienteEmpresaAgua("http://127.0.0.1:" + servidor.getAddress().getPort(), 500, 500, json);
    }

    @AfterEach void cerrar() { servidor.stop(0); hilos.shutdownNow(); }

    @ParameterizedTest @EnumSource(Punto.class)
    void aceptaElContratoValidoYConservaLasUnidadesIdentificadoresYFechas(Punto punto) {
        var resultado = invocar(punto);
        assertTrue(resultado.isPresent());
        var valor = resultado.orElseThrow();
        switch (punto) {
            case SUMINISTRO -> assertEquals(CODIGO, ((pe.nivora.backend.acceso.dominio.Suministro) valor).codigo());
            case INVENTARIO, CORTE -> {
                var contexto = (ContextoLecturas) valor;
                assertEquals("3", contexto.hastaIdLectura());
                assertEquals(RELOJ.instant(), contexto.confirmadoHasta());
                assertEquals("1", contexto.medidores().getFirst().idMedidor());
            }
            case LECTURAS -> {
                var lote = (pe.nivora.backend.lecturas.dominio.LoteLecturas) valor;
                assertEquals(3, lote.lecturas().size());
                assertEquals(3100L, lote.lecturas().getLast().valorLitros());
                assertEquals("3", lote.siguienteCursor());
                assertTrue(consultas.getLast().contains("hastaId=3"));
            }
            case TARIFAS -> assertEquals("2.5000", ((pe.nivora.backend.tarifas.dominio.HistorialTarifario) valor)
                .asignaciones().getFirst().tarifa().tramos().getFirst().precioAguaPorM3().toPlainString());
            case RECIBOS -> assertEquals("7.50", ((pe.nivora.backend.recibos.dominio.RecibosEmitidos) valor)
                .recibos().getFirst().importeTotal().toPlainString());
        }
    }

    @ParameterizedTest @MethodSource("fallos")
    void clasificaLosFallosSinAceptarDatosIncompatiblesNiSeguirRedirecciones(Punto punto, String fallo) {
        alterar = punto;
        estado = 200; contenido = "application/json";
        cuerpo = switch (fallo) {
            case "json" -> "{incompleto";
            case "duplicados" -> {
                String valido = json.writeValueAsString(datos.path(punto.seccion));
                String campo = valido.substring(1, valido.indexOf(':'));
                yield "{" + campo + ":null," + valido.substring(1);
            }
            case "trailing" -> json.writeValueAsString(datos.path(punto.seccion)) + " {}";
            case "grande" -> json.writeValueAsString(datos.path(punto.seccion)) + " ".repeat(512 * 1024 + 1);
            case "media" -> { contenido = "text/plain"; yield json.writeValueAsString(datos.path(punto.seccion)); }
            case "404-generico" -> { estado = 404; contenido = "application/problem+json"; yield "{\"status\":404,\"title\":\"Not Found\",\"instance\":\"/otra\"}"; }
            default -> { estado = Integer.parseInt(fallo); yield "{}"; }
        };
        var error = assertThrows(punto.error, () -> invocar(punto));
        assertFalse(error.getMessage().contains("Titular ficticio"));
        assertTrue(consultas.stream().noneMatch(x -> x.contains("no-se-debe-seguir")));
    }

    static Stream<Arguments> fallos() {
        return Stream.of(Punto.values()).flatMap(p -> Stream.of("json", "duplicados", "trailing", "grande", "media", "404-generico", "302", "401", "429", "503")
            .map(f -> Arguments.of(p, f)));
    }

    @ParameterizedTest @EnumSource(Punto.class)
    void laAusenciaSoloSeAceptaCuandoCoincideConElContratoDelSuministro(Punto punto) {
        alterar = punto; estado = 404; contenido = "application/problem+json";
        cuerpo = "{\"status\":404,\"title\":\"Suministro no encontrado\",\"instance\":\"" + punto.ruta + "\"}";
        if (punto == Punto.CORTE) assertThrows(punto.error, () -> invocar(punto));
        else assertTrue(invocar(punto).isEmpty());
    }

    @ParameterizedTest @EnumSource(Punto.class)
    void elTiempoDeLecturaAgotadoSeClasificaComoFuenteNoDisponible(Punto punto) {
        alterar = punto; estado = 200; contenido = "application/json";
        cuerpo = json.writeValueAsString(datos.path(punto.seccion)); demora = 1000;
        assertThrows(punto.error, () -> invocar(punto));
    }

    private java.util.Optional<?> invocar(Punto punto) {
        return switch (punto) {
            case SUMINISTRO -> new FuenteSuministrosHttp(cliente).consultar(CODIGO);
            case INVENTARIO, CORTE -> new FuenteLecturasConCorteHttp(cliente).consultarContexto(CODIGO);
            case LECTURAS -> new FuenteLecturasConCorteHttp(cliente).consultar(CODIGO, "0", 10,
                ContextoLecturasJson.interpretar(datos.path("corte"), datos.path("inventario"), CODIGO));
            case TARIFAS -> new FuenteTarifasHttp(cliente).consultar(CODIGO);
            case RECIBOS -> new FuenteRecibosHttp(cliente, RELOJ).consultar(CODIGO, 12);
        };
    }
}
