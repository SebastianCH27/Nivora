package pe.nivora.backend.lecturas.infraestructura.empresaagua;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;

@Configuration(proxyBeanMethods = false)
public class LecturasEmpresaConfiguracion {
    @Bean
    FuenteLecturas fuenteLecturas(ClienteEmpresaAgua cliente) {
        return new FuenteLecturasHttp(cliente);
    }
}
