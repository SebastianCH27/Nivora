package pe.nivora.backend.acceso.infraestructura.empresaagua;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;

@Configuration(proxyBeanMethods = false)
public class EmpresaAguaConfiguracion {

    @Bean
    FuenteSuministros fuenteSuministros(
            @Value("${nivora.empresa-agua.url-base}") String urlBase,
            @Value("${nivora.empresa-agua.conexion-ms}") int conexionMs,
            @Value("${nivora.empresa-agua.lectura-ms}") int lecturaMs,
            JsonMapper json) {
        return new FuenteSuministrosHttp(urlBase, conexionMs, lecturaMs, json);
    }
}
