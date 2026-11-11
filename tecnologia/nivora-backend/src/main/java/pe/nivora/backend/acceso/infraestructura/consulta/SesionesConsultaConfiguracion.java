package pe.nivora.backend.acceso.infraestructura.consulta;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.acceso.aplicacion.SesionesConsulta;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSesionesConsulta;

@Configuration(proxyBeanMethods = false)
public class SesionesConsultaConfiguracion {

    @Bean
    SesionesConsulta sesionesConsulta(FuenteSuministros suministros, FuenteIdentidad identidades,
            RepositorioSesionesConsulta repositorio, Clock reloj,
            @Value("${nivora.sesiones-consulta.duracion-segundos}") long duracion,
            @Value("${nivora.sesiones-consulta.ventana-intentos-segundos}") long ventana,
            @Value("${nivora.sesiones-consulta.intentos-por-origen}") int intentos) {
        return new SesionesConsulta(suministros, identidades, repositorio, reloj,
            Duration.ofSeconds(duracion), Duration.ofSeconds(ventana), intentos);
    }
}
