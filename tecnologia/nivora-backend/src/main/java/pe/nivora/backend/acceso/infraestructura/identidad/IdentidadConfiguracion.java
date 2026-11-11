package pe.nivora.backend.acceso.infraestructura.identidad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException.Motivo;

@Configuration(proxyBeanMethods = false)
public class IdentidadConfiguracion {

    @Bean
    FuenteIdentidad fuenteIdentidad(JsonMapper json) {
        String token = System.getenv("NIVORA_IDENTIDAD_API_TOKEN");
        if (token == null || token.isBlank()) {
            return dni -> {
                throw new FuenteIdentidadNoDisponibleException(Motivo.SIN_CONFIGURACION);
            };
        }
        return new FuenteIdentidadApisPeru(token.strip(), json);
    }
}
