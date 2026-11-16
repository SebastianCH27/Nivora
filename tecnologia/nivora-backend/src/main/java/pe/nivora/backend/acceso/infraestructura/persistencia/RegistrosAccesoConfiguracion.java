package pe.nivora.backend.acceso.infraestructura.persistencia;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.acceso.aplicacion.ConsultarRegistrosAcceso;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso;

@Configuration(proxyBeanMethods = false)
public class RegistrosAccesoConfiguracion {
    @Bean
    ConsultarRegistrosAcceso consultarRegistrosAcceso(ConsultaRegistrosAcceso consulta) {
        return new ConsultarRegistrosAcceso(consulta);
    }
}
