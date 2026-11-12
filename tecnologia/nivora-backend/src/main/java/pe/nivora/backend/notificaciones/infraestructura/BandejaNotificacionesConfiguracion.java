package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.notificaciones.aplicacion.BandejaNotificaciones;
import pe.nivora.backend.notificaciones.dominio.RepositorioBandejaNotificaciones;

@Configuration(proxyBeanMethods = false)
public class BandejaNotificacionesConfiguracion {
    @Bean
    BandejaNotificaciones bandejaNotificaciones(RepositorioBandejaNotificaciones repositorio, Clock reloj) {
        return new BandejaNotificaciones(repositorio, reloj);
    }
}
