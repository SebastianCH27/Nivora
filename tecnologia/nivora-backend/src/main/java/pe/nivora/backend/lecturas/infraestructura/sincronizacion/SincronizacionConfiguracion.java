package pe.nivora.backend.lecturas.infraestructura.sincronizacion;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pe.nivora.backend.lecturas.aplicacion.PoliticaSincronizacion;
import pe.nivora.backend.lecturas.aplicacion.RecuperarLecturasConCorte;
import pe.nivora.backend.lecturas.aplicacion.SincronizarLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioSincronizacion;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "nivora.sincronizacion.habilitada", havingValue = "true")
public class SincronizacionConfiguracion {
    @Bean
    PoliticaSincronizacion politicaSincronizacion(
            @Value("${nivora.sincronizacion.maximo-intentos-por-ciclo}") int intentos,
            @Value("${nivora.sincronizacion.limite-pagina}") int limite,
            @Value("${nivora.sincronizacion.maximo-paginas-por-intento}") int paginas,
            @Value("${nivora.sincronizacion.reserva-segundos}") long reserva,
            @Value("${nivora.sincronizacion.intervalo-completo-segundos}") long completo,
            @Value("${nivora.sincronizacion.continuacion-segundos}") long continuacion,
            @Value("${nivora.sincronizacion.reintento-inicial-segundos}") long inicial,
            @Value("${nivora.sincronizacion.reintento-maximo-segundos}") long maximo) {
        return new PoliticaSincronizacion(intentos, limite, paginas, Duration.ofSeconds(reserva),
            Duration.ofSeconds(completo), Duration.ofSeconds(continuacion),
            Duration.ofSeconds(inicial), Duration.ofSeconds(maximo));
    }

    @Bean
    SincronizarLecturas sincronizarLecturas(RepositorioSincronizacion programacion,
            RecuperarLecturasConCorte recuperar, PoliticaSincronizacion politica, Clock reloj) {
        return new SincronizarLecturas("empresa-agua", programacion,
            codigo -> recuperar.ejecutar(codigo, politica.limitePagina(), politica.maximoPaginas()),
            politica, reloj);
    }
}
