package pe.nivora.backend.lecturas.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ConsultarSupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaSupervisionLecturas;

@Configuration(proxyBeanMethods = false)
public class SupervisionLecturasConfiguracion {
    @Bean
    ConsultarSupervisionLecturas consultarSupervisionLecturas(
            ConsultaSupervisionLecturas consulta, Clock reloj) {
        return new ConsultarSupervisionLecturas(consulta, reloj);
    }
}
