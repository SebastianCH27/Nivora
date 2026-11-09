package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ImportarPaginaLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;

@Configuration(proxyBeanMethods = false)
public class LecturasPersistenciaConfiguracion {
    @Bean
    ImportarPaginaLecturas importarPaginaLecturas(FuenteLecturas fuente,
                                                 RepositorioLecturas repositorio, Clock reloj) {
        return new ImportarPaginaLecturas(fuente, repositorio, reloj);
    }
}
