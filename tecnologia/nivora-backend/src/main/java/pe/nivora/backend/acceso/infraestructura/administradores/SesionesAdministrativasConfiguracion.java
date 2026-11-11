package pe.nivora.backend.acceso.infraestructura.administradores;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.nivora.backend.acceso.aplicacion.SesionesAdministrativas;
import pe.nivora.backend.acceso.dominio.administradores.PoliticaSesionAdministrativa;
import pe.nivora.backend.acceso.dominio.contratos.*;

@Configuration(proxyBeanMethods = false)
public class SesionesAdministrativasConfiguracion {
    @Bean
    PoliticaSesionAdministrativa politicaSesionAdministrativa(
            @Value("${nivora.sesiones-administrativas.inactividad-segundos}") long inactividad,
            @Value("${nivora.sesiones-administrativas.duracion-maxima-segundos}") long maxima,
            @Value("${nivora.sesiones-administrativas.ventana-intentos-segundos}") long ventana,
            @Value("${nivora.sesiones-administrativas.intentos-por-usuario}") int usuario,
            @Value("${nivora.sesiones-administrativas.intentos-por-origen}") int origen) {
        return new PoliticaSesionAdministrativa(Duration.ofSeconds(inactividad), Duration.ofSeconds(maxima),
            Duration.ofSeconds(ventana), usuario, origen);
    }

    @Bean
    SesionesAdministrativas sesionesAdministrativas(RepositorioSesionesAdministrativas repositorio,
            ProtectorClavesAdministrativas claves, Clock reloj, PoliticaSesionAdministrativa politica) {
        return new SesionesAdministrativas(repositorio, claves, reloj, politica);
    }
}
