package pe.nivora.backend.acceso.infraestructura.empresaagua;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;

@Configuration(proxyBeanMethods = false)
public class EmpresaAguaConfiguracion {

    @Bean
    ClienteEmpresaAgua clienteEmpresaAgua(
            @Value("${nivora.empresa-agua.url-base}") String urlBase,
            @Value("${nivora.empresa-agua.conexion-ms}") int conexionMs,
            @Value("${nivora.empresa-agua.lectura-ms}") int lecturaMs,
            JsonMapper json) {
        return new ClienteEmpresaAgua(urlBase, conexionMs, lecturaMs, json);
    }

    @Bean
    FuenteSuministros fuenteSuministros(ClienteEmpresaAgua cliente) {
        return new FuenteSuministrosHttp(cliente);
    }
}
