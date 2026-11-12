package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.notificaciones.aplicacion.GenerarAvisosRecibo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAvisosRecibo;

@Configuration(proxyBeanMethods = false)
public class AvisosReciboConfiguracion {
    private static final Logger LOG = LoggerFactory.getLogger(AvisosReciboConfiguracion.class);

    @Bean
    GenerarAvisosRecibo generarAvisosRecibo(RepositorioAvisosRecibo repositorio, Clock reloj) {
        return new GenerarAvisosRecibo(repositorio, reloj);
    }

    @Bean
    @ConditionalOnProperty(name = "nivora.avisos-recibo.comprobar-al-iniciar", havingValue = "true")
    ApplicationRunner comprobarAvisosRecibo(GenerarAvisosRecibo generar) {
        return argumentos -> LOG.info("Avisos de recibo: nuevos {}.", generar.ejecutar());
    }
}
