package pe.nivora.backend.lecturas.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;

@Configuration(proxyBeanMethods = false)
public class ConsumoActualConfiguracion {
    @Bean
    ConsultarConsumoActual consultarConsumoActual(ConsultaLecturasPeriodo consulta, Clock reloj) {
        return new ConsultarConsumoActual(consulta, reloj);
    }

    @Bean
    ConsultarHistorialMensual consultarHistorialMensual(ConsultaLecturasPeriodo consulta, Clock reloj) {
        return new ConsultarHistorialMensual(consulta, reloj);
    }

    @Bean
    ConsultarEstadisticasConsumo consultarEstadisticasConsumo(ConsultaLecturasPeriodo consulta, Clock reloj) {
        return new ConsultarEstadisticasConsumo(consulta, reloj);
    }
}
