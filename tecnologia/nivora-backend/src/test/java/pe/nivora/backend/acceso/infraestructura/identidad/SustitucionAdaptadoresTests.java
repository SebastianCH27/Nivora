package pe.nivora.backend.acceso.infraestructura.identidad;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.nivora.backend.NivoraBackendApplication;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasConCorte;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.integracion.contratos.AdaptadoresAguaArchivo;
import static org.junit.jupiter.api.Assertions.*;

class SustitucionAdaptadoresTests {
    @Test void sustituyeLosCuatroAdaptadoresYConservaLasOchoConsultasYLaIdempotencia() throws Exception {
        var peticiones = new AtomicInteger();
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        EscenarioCierreBackend.publicar(servidor, peticiones); servidor.start();
        String origen = "http://localhost:" + servidor.getAddress().getPort();
        try (var cliente = HttpClient.newHttpClient()) {
            java.util.Map<String, tools.jackson.databind.JsonNode> referencia;
            try (var contexto = new SpringApplicationBuilder(NivoraBackendApplication.class, EscenarioCierreBackend.Composicion.class)
                    .profiles("test").run(argumentos("http", origen))) {
                referencia = EscenarioCierreBackend.flujo(contexto, cliente, "http");
                assertTrue(peticiones.get() >= 7);
            }
            peticiones.set(0);
            try (var contexto = new SpringApplicationBuilder(NivoraBackendApplication.class,
                    EscenarioCierreBackend.Composicion.class, ComposicionArchivo.class)
                    .profiles("test").run(argumentos("archivo", origen))) {
                var sustituida = EscenarioCierreBackend.flujo(contexto, cliente, "http");
                assertEquals(referencia, sustituida);
                // Solo identidad usa HTTP; suministro, inventario/corte/pagina, tarifas y recibos salen del archivo.
                assertEquals(1, peticiones.get());
            }
        } finally { servidor.stop(0); }
    }
    private static String[] argumentos(String base, String origen) {
        return new String[]{"--server.port=0", "--nivora.empresa-agua.url-base=" + origen,
            "--spring.datasource.url=jdbc:h2:mem:sustitucion_" + base + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora"};
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ComposicionArchivo {
        @Bean AdaptadoresAguaArchivo archivo() throws Exception { return new AdaptadoresAguaArchivo(); }
        @Bean @Primary FuenteSuministros suministrosArchivo(AdaptadoresAguaArchivo a) { return a.suministros(); }
        @Bean @Primary FuenteLecturasConCorte lecturasArchivo(AdaptadoresAguaArchivo a) { return a.lecturas(); }
        @Bean @Primary FuenteTarifas tarifasArchivo(AdaptadoresAguaArchivo a) { return a.tarifas(); }
        @Bean @Primary FuenteRecibos recibosArchivo(AdaptadoresAguaArchivo a) { return a.recibos(); }
    }
}
