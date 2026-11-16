package pe.nivora.backend.integracion.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.integracion.aplicacion.SolicitarReintentoIntegracion;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion;

@Configuration(proxyBeanMethods = false)
public class ReintentosIntegracionConfiguracion {
    @Bean
    SolicitarReintentoIntegracion solicitarReintentoIntegracion(RepositorioReintentosIntegracion reintentos,
            Clock reloj) {
        return new SolicitarReintentoIntegracion(reintentos, reloj);
    }
}
