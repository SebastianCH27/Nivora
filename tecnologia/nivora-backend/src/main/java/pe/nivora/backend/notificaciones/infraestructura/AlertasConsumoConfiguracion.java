package pe.nivora.backend.notificaciones.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.notificaciones.aplicacion.EvaluarAlertaConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAlertasConsumo;

@Configuration(proxyBeanMethods = false)
public class AlertasConsumoConfiguracion {
    @Bean
    EvaluarAlertaConsumo evaluarAlertaConsumo(ConsultarEstadisticasConsumo estadisticas,
            RepositorioAlertasConsumo repositorio) {
        return new EvaluarAlertaConsumo(estadisticas, repositorio);
    }
}
