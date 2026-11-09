package pe.nivora.backend.acceso.infraestructura.persistencia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.acceso.aplicacion.ActualizarSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;

@Configuration(proxyBeanMethods = false)
public class SuministrosConfiguracion {

    @Bean
    Clock relojNivora() {
        return Clock.systemUTC();
    }

    @Bean
    ActualizarSuministro actualizarSuministro(FuenteSuministros fuente,
                                            RepositorioSuministros repositorio, Clock reloj) {
        return new ActualizarSuministro(fuente, repositorio, reloj);
    }
}
