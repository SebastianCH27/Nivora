package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.RecuperarLecturasConCorte;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasConCorte;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;

@Configuration(proxyBeanMethods = false)
public class RecuperacionLecturasConfiguracion {
    @Bean
    RecuperarLecturasConCorte recuperarLecturas(FuenteLecturasConCorte fuente,
            RepositorioRecuperacionLecturas repositorio, Clock reloj) {
        return new RecuperarLecturasConCorte("empresa-agua", fuente, repositorio, reloj);
    }
}
