package pe.nivora.backend.recibos.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.recibos.aplicacion.ConsultarRecibos;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

@Configuration(proxyBeanMethods = false)
public class RecibosConfiguracion {
    @Bean
    IncorporarRecibos incorporarRecibos(FuenteRecibos fuente, RepositorioRecibos repositorio, Clock reloj) {
        return new IncorporarRecibos(fuente, repositorio, reloj);
    }

    @Bean
    ConsultarRecibos consultarRecibos(RepositorioRecibos repositorio) {
        return new ConsultarRecibos(repositorio);
    }
}
