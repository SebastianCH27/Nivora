package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.notificaciones.aplicacion.DespacharWebPush;
import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.web-push.habilitado", havingValue = "true")
public class WebPushConfiguracion {
    private static final Logger LOG = LoggerFactory.getLogger(WebPushConfiguracion.class);
    private final DespacharWebPush despachar;

    public WebPushConfiguracion(DespacharWebPush despachar) {
        this.despachar = despachar;
    }

    @Bean
    static EmisorWebPush emisorWebPush(@Value("${NIVORA_WEB_PUSH_PUBLICA:}") String publica,
            @Value("${NIVORA_WEB_PUSH_PRIVADA:}") String privada, @Value("${NIVORA_WEB_PUSH_CONTACTO:}") String contacto,
            JsonMapper json) {
        return new EmisorWebPushHttp(publica, privada, contacto, json);
    }

    @Bean
    static DespacharWebPush despacharWebPush(RepositorioWebPush repositorio, EmisorWebPush emisor, Clock reloj) {
        return new DespacharWebPush(repositorio, emisor, reloj);
    }

    @Scheduled(initialDelayString = "${nivora.web-push.intervalo-ms}", fixedDelayString = "${nivora.web-push.intervalo-ms}")
    public void ejecutar() {
        try {
            despachar.ejecutar();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            LOG.warn("No se pudo completar el ciclo Web Push. Causa: {}.", error.getClass().getSimpleName());
        }
    }
}
