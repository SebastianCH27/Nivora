package pe.nivora.backend.lecturas.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

@Configuration(proxyBeanMethods = false)
public class ConsumoActualConfiguracion {
    @Bean
    ConsultarConsumoActual consultarConsumoActual(ConsultaLecturasPeriodo consulta,
            RepositorioPeriodosMensuales periodos, Clock reloj) {
        return new ConsultarConsumoActual(consulta, periodos, reloj);
    }

    @Bean
    ConsultarHistorialMensual consultarHistorialMensual(ConsultaLecturasPeriodo consulta,
            RepositorioPeriodosMensuales periodos, Clock reloj) {
        return new ConsultarHistorialMensual(consulta, periodos, reloj);
    }

    @Bean
    ConsultarEstadisticasConsumo consultarEstadisticasConsumo(ConsultaLecturasPeriodo consulta,
            RepositorioPeriodosMensuales periodos, Clock reloj) {
        return new ConsultarEstadisticasConsumo(consulta, periodos, reloj);
    }
}
