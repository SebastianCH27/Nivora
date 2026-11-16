package pe.nivora.backend.integracion.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.integracion.aplicacion.ConsultarIncidenciasIntegracion;
import pe.nivora.backend.integracion.dominio.ConsultaIncidenciasIntegracion;

@Configuration(proxyBeanMethods = false)
public class IncidenciasIntegracionConfiguracion {
    @Bean
    ConsultarIncidenciasIntegracion consultarIncidenciasIntegracion(ConsultaIncidenciasIntegracion consulta) {
        return new ConsultarIncidenciasIntegracion(consulta);
    }
}
