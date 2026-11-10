package pe.nivora.backend.acceso.infraestructura.administradores;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;

import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.contratos.ProtectorClavesAdministrativas;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioAdministradores;

@Configuration(proxyBeanMethods = false)
public class AdministradoresConfiguracion {
    @Bean
    ProtectorClavesAdministrativas protectorClavesAdministrativas(ApplicationContext contexto,
            @Value("${nivora.administradores.permitir-clave-corta-inicial:false}") boolean corta,
            @Value("${nivora.administradores.inicializar-al-iniciar:false}") boolean inicializar) {
        if (corta && (!inicializar || contexto instanceof WebApplicationContext)) {
            throw new IllegalArgumentException(
                "La clave corta inicial solo se admite en la inicializacion no web explicita.");
        }
        return new ProtectorClavesArgon2(corta);
    }

    @Bean
    InicializarAdministracion inicializarAdministracion(RepositorioAdministradores repositorio,
            ProtectorClavesAdministrativas claves, Clock reloj) {
        return new InicializarAdministracion(repositorio, claves, reloj);
    }
}
