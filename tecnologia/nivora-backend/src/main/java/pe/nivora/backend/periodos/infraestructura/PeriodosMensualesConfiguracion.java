package pe.nivora.backend.periodos.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.periodos.aplicacion.GestionarPeriodosMensuales;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

@Configuration(proxyBeanMethods = false)
public class PeriodosMensualesConfiguracion {
    @Bean
    GestionarPeriodosMensuales gestionarPeriodosMensuales(RepositorioPeriodosMensuales repositorio, Clock reloj) {
        return new GestionarPeriodosMensuales(repositorio, reloj);
    }
}
