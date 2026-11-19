package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.lecturas.aplicacion.RecuperarLecturasConCorte;
import pe.nivora.backend.notificaciones.aplicacion.EvaluarAlertaConsumo;
import pe.nivora.backend.notificaciones.aplicacion.GenerarAvisosRecibo;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.tarifas.aplicacion.SincronizarTarifas;
import static org.junit.jupiter.api.Assertions.*;

final class EscenarioCierreBackend {
    static final Instant AHORA = Instant.parse("2026-11-11T13:00:00Z");
    static final CodigoSuministro CODIGO = new CodigoSuministro("000101");
    static final String CONSULTA = "/api/v1/acceso/consulta/";
    static final List<String> OPERACIONES = List.of("consumo-actual", "historial", "estadisticas",
        "costos", "proyeccion", "recibos", "notificaciones", "comparacion");
    static final JsonMapper JSON = new JsonMapper();
    static final JsonNode DATOS = leer();
    private static JsonNode leer() {
        try (var entrada = EscenarioCierreBackend.class.getResourceAsStream("/contratos/empresa-agua.json")) {
            return JSON.readTree(entrada);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    static void publicar(HttpServer servidor, AtomicInteger consultas) {
        servidor.createContext("/", intercambio -> {
            consultas.incrementAndGet();
            String ruta = intercambio.getRequestURI().getPath();
            String clave = ruta.startsWith("/api/v1/dni/") ? "identidad"
                : ruta.equals("/api/v1/lecturas/corte") ? "corte"
                : ruta.endsWith("/medidores") ? "inventario"
                : ruta.endsWith("/lecturas") ? "pagina"
                : ruta.endsWith("/tarifas") ? "tarifas" : ruta.endsWith("/recibos") ? "recibos" : "suministro";
            if (clave.equals("identidad")) assertEquals("Bearer token-ficticio", intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, DATOS.path(clave));
        });
    }
    private static void responder(HttpExchange intercambio, JsonNode datos) throws IOException {
        byte[] cuerpo = JSON.writeValueAsBytes(datos);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (var salida = intercambio.getResponseBody()) { salida.write(cuerpo); }
    }
    static void esquema(ConfigurableApplicationContext contexto) throws Exception {
        try (var conexion = contexto.getBean(DataSource.class).getConnection()) {
            var archivos = java.nio.file.Files.list(java.nio.file.Path.of("base-datos"));
            try (archivos) {
                var recursos = archivos.filter(p -> p.getFileName().toString().matches("(0[2-9]|1[0-9]|20|22)-.*\\.sql"))
                    .sorted().map(p -> new FileSystemResource(p)).toArray(FileSystemResource[]::new);
                new ResourceDatabasePopulator(recursos).populate(conexion);
            }
        }
    }
    static URI origen(ConfigurableApplicationContext contexto, String protocolo) {
        return URI.create(protocolo + "://localhost:" + contexto.getEnvironment().getProperty("local.server.port"));
    }
    static HttpResponse<String> pedir(HttpClient cliente, URI origen, String metodo, String ruta,
            String token, String cuerpo) throws Exception {
        var builder = HttpRequest.newBuilder(origen.resolve(ruta)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (cuerpo != null) builder.header("Content-Type", "application/json");
        return cliente.send(builder.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static Map<String, JsonNode> flujo(ConfigurableApplicationContext contexto, HttpClient http, String protocolo) throws Exception {
        esquema(contexto);
        var origen = origen(contexto, protocolo);
        assertEquals(401, pedir(http, origen, "GET", CONSULTA + "consumo-actual", null, null).statusCode());
        var inicio = pedir(http, origen, "POST", CONSULTA + "sesiones", null,
            "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"}");
        assertEquals(201, inicio.statusCode(), inicio.body());
        assertTrue(inicio.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String token = JSON.readTree(inicio.body()).path("token").stringValue();
        assertNotNull(token);
        var recuperacion = contexto.getBean(RecuperarLecturasConCorte.class).ejecutar(CODIGO, 100, 2);
        assertEquals(3, recuperacion.incorporacion().nuevas());
        contexto.getBean(SincronizarTarifas.class).ejecutarCiclo();
        assertTrue(contexto.getBean(IncorporarRecibos.class).ejecutar(CODIGO, 12).isPresent());
        var alertas = contexto.getBean(EvaluarAlertaConsumo.class);
        assertEquals(EvaluarAlertaConsumo.Estado.GENERADA, alertas.ejecutar(CODIGO).estado());
        assertEquals(EvaluarAlertaConsumo.Estado.YA_REGISTRADA, alertas.ejecutar(CODIGO).estado());
        var avisos = contexto.getBean(GenerarAvisosRecibo.class);
        assertEquals(1, avisos.ejecutar()); assertEquals(0, avisos.ejecutar());
        var respuestas = new LinkedHashMap<String, JsonNode>();
        for (String ruta : OPERACIONES) {
            var respuesta = pedir(http, origen, "GET", CONSULTA + ruta, token, null);
            assertEquals(200, respuesta.statusCode(), ruta + ": " + respuesta.body());
            respuestas.put(ruta, JSON.readTree(respuesta.body()));
        }
        assertEquals("2000", respuestas.get("consumo-actual").at("/consumo/litros").stringValue());
        assertEquals("1000", respuestas.get("estadisticas").at("/comparacion/anterior/consumo/litros").stringValue());
        assertEquals("10.00", respuestas.get("costos").at("/actual/desglose/totalEstimado").stringValue());
        assertEquals("7.50", respuestas.get("recibos").at("/recibos/0/importeTotal").stringValue());
        assertEquals("6000", respuestas.get("proyeccion").at("/proyeccion/litros").stringValue());
        assertEquals("20.00", respuestas.get("proyeccion").at("/costoProyectado/desglose/totalEstimado").stringValue());
        assertEquals(2, respuestas.get("notificaciones").path("notificaciones").size());
        assertEquals("2000", respuestas.get("comparacion").at("/comparado/estimacion/consumo/litros").stringValue());
        assertEquals(401, pedir(http, origen, "GET", "/api/v1/administracion/suministros", token, null).statusCode());
        assertEquals(403, pedir(http, origen, "POST", CONSULTA + "consumo-actual", token, "{}").statusCode());
        assertEquals(204, pedir(http, origen, "DELETE", CONSULTA + "sesion", token, null).statusCode());
        assertEquals(401, pedir(http, origen, "GET", CONSULTA + "consumo-actual", token, null).statusCode());
        return respuestas;
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Composicion {
        @Bean @Primary Clock relojCierre() { return Clock.fixed(AHORA, ZoneOffset.UTC); }
        @Bean @Primary FuenteIdentidad identidadCierre(org.springframework.core.env.Environment entorno, JsonMapper json) {
            return new FuenteIdentidadApisPeru(URI.create(entorno.getRequiredProperty("nivora.empresa-agua.url-base")), "token-ficticio", json);
        }
    }
}
