package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.RecuperarLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;

@Configuration(proxyBeanMethods = false)
public class RecuperacionLecturasConfiguracion {
    @Bean
    RecuperarLecturas recuperarLecturas(FuenteLecturas fuente,
            RepositorioRecuperacionLecturas repositorio, Clock reloj) {
        return new RecuperarLecturas("empresa-agua", fuente, repositorio, reloj);
    }
}
