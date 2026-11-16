package pe.nivora.backend.recibos.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;
import pe.nivora.backend.recibos.aplicacion.ConsultarRecibos;
import pe.nivora.backend.recibos.aplicacion.ConsultarSupervisionRecibos;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.dominio.ConsultaSupervisionRecibos;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

@Configuration(proxyBeanMethods = false)
public class RecibosConfiguracion {
    @Bean
    IncorporarRecibos incorporarRecibos(FuenteRecibos fuente, RepositorioRecibos repositorio, Clock reloj) {
        return new IncorporarRecibos(fuente, repositorio, reloj);
    }

    @Bean
    ConsultarRecibos consultarRecibos(RepositorioRecibos repositorio, RepositorioPeriodosMensuales periodos) {
        return new ConsultarRecibos(repositorio, periodos);
    }

    @Bean
    ConsultarSupervisionRecibos consultarSupervisionRecibos(ConsultaSupervisionRecibos consulta, Clock reloj) {
        return new ConsultarSupervisionRecibos(consulta, reloj);
    }
}
