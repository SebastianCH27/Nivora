package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.notificaciones.aplicacion.ConfigurarNotificaciones;
import pe.nivora.backend.notificaciones.aplicacion.EvaluarAlertaConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAlertasConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones;

@Configuration(proxyBeanMethods = false)
public class AlertasConsumoConfiguracion {
    @Bean
    ConfigurarNotificaciones configurarNotificaciones(RepositorioConfiguracionNotificaciones repositorio, Clock reloj,
            @Value("${nivora.alertas-consumo.habilitada}") boolean habilitadaPorServidor) {
        return new ConfigurarNotificaciones(repositorio, reloj, habilitadaPorServidor);
    }

    @Bean
    EvaluarAlertaConsumo evaluarAlertaConsumo(ConsultarEstadisticasConsumo estadisticas,
            RepositorioAlertasConsumo repositorio, ConfigurarNotificaciones configuracion, Clock reloj) {
        return new EvaluarAlertaConsumo(estadisticas, repositorio, configuracion, reloj);
    }
}
