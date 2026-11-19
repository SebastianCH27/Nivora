package pe.nivora.backend.tarifas;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.nivora.backend.acceso.dominio.IdentidadConsultada;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import static org.junit.jupiter.api.Assertions.*;

/** Comprueba el planificador real, sin invocar manualmente el trabajador ni el importador. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "nivora.tarifas.sincronizacion.habilitada=true",
    "nivora.tarifas.sincronizacion.espera-inicial-ms=100",
    "nivora.tarifas.sincronizacion.intervalo-ms=100"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TarifasAutomaticasTests {
    private static final AtomicInteger llamadas = new AtomicInteger();
    private static final HttpServer empresa = iniciarEmpresa();
    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Value("${local.server.port}") private int puerto;

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_tarifas_automaticas;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base", () -> "http://127.0.0.1:" + empresa.getAddress().getPort());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FuentesPrueba {
        @Bean @Primary FuenteSuministros suministroFicticio() {
            return codigo -> Optional.of(new Suministro(codigo, "Titular ficticio"));
        }
        @Bean @Primary FuenteIdentidad identidadFicticia() {
            return dni -> new IdentidadConsultada(dni, "Persona ficticia");
        }
    }

    @Test void elAltaHttpDesencadenaLaPrimeraImportacionMedianteElPlanificador() throws Exception {
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
            new FileSystemResource("base-datos/09-sesiones-consulta.sql"),
            new FileSystemResource("base-datos/10-tarifas.sql"),
            new FileSystemResource("base-datos/22-sincronizacion-tarifas.sql")).execute(datos);
        try (var http = HttpClient.newHttpClient()) {
            var solicitud = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto
                + "/api/v1/acceso/consulta/sesiones"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"}")).build();
            assertEquals(201, http.send(solicitud, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < limite && jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "programacion_sincronizacion_tarifas WHERE ultimo_resultado = 'ACTUALIZADO'", Integer.class) == 0) {
            Thread.sleep(25);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas_historiales WHERE codigo_suministro = '000101'", Integer.class));
        assertEquals("ACTUALIZADO", jdbc.queryForObject("SELECT ultimo_resultado FROM programacion_sincronizacion_tarifas", String.class));
        assertEquals(1, llamadas.get());
    }

    @AfterAll static void cerrar() { empresa.stop(0); }

    private static HttpServer iniciarEmpresa() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/suministros/000101/tarifas", solicitud -> {
                try (solicitud) {
                    llamadas.incrementAndGet();
                    byte[] cuerpo = """
                        {"fuente":"empresa-agua","codigoSuministro":"000101","moneda":"PEN","zonaFechas":"America/Lima",
                         "asignaciones":[{"codigoTarifa":"DOMESTICA","versionTarifa":1,"nombreTarifa":"Demostracion",
                          "cargoFijo":5,"igvPorcentaje":0,"vigenteDesde":"2026-10-01","vigenteHasta":null,
                          "asignadaDesde":"2026-10-01","asignadaHasta":null,"aplicableDesde":"2026-10-01","aplicableHasta":null,
                          "tramos":[{"desdeM3":0,"hastaM3":null,"precioPorM3":2.50,"precioAlcantarilladoPorM3":0}]}]}
                        """.getBytes(StandardCharsets.UTF_8);
                    solicitud.getResponseHeaders().set("Content-Type", "application/json");
                    solicitud.sendResponseHeaders(200, cuerpo.length);
                    solicitud.getResponseBody().write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("No se pudo iniciar la fuente ficticia.", error);
        }
    }
}
