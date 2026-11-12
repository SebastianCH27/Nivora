package pe.nivora.backend.notificaciones.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import pe.nivora.backend.notificaciones.aplicacion.GenerarAvisosRecibo;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.avisos-recibo.habilitados", havingValue = "true")
public class AvisosReciboAutomaticos {
    private static final Logger LOG = LoggerFactory.getLogger(AvisosReciboAutomaticos.class);
    private final GenerarAvisosRecibo generar;

    public AvisosReciboAutomaticos(GenerarAvisosRecibo generar) {
        this.generar = generar;
    }

    @Scheduled(initialDelayString = "${nivora.avisos-recibo.espera-inicial-ms}",
        fixedDelayString = "${nivora.avisos-recibo.intervalo-ms}")
    public void actualizar() {
        try {
            int nuevos = generar.ejecutar();
            if (nuevos > 0) LOG.info("Avisos de recibo: nuevos {}.", nuevos);
        } catch (RuntimeException error) {
            LOG.warn("No se pudieron revisar los avisos de recibo. Se volvera a intentar.");
            LOG.debug("Detalle del fallo al registrar avisos de recibo", error);
        }
    }
}
