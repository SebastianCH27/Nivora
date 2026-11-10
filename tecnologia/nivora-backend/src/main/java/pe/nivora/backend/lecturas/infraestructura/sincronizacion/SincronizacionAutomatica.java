package pe.nivora.backend.lecturas.infraestructura.sincronizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pe.nivora.backend.lecturas.aplicacion.SincronizarLecturas;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.sincronizacion.habilitada", havingValue = "true")
public class SincronizacionAutomatica {
    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionAutomatica.class);
    private final SincronizarLecturas sincronizar;

    public SincronizacionAutomatica(SincronizarLecturas sincronizar) {
        this.sincronizar = sincronizar;
    }

    @Scheduled(initialDelayString = "${nivora.sincronizacion.espera-inicial-ms}",
        fixedDelayString = "${nivora.sincronizacion.intervalo-ms}")
    public void actualizar() {
        try {
            for (var intento : sincronizar.ejecutarCiclo()) {
                if (!intento.programacionConfirmada()) {
                    LOG.warn("Sincronizacion: suministro {}; resultado {}; reserva vencida, reemplazada o sin confirmar. Se conserva el progreso.",
                        intento.reserva().suministro().valor(), intento.estado());
                } else if (intento.error() != null) {
                    LOG.warn("Sincronizacion: suministro {}; resultado {}; proximo intento UTC {}.",
                        intento.reserva().suministro().valor(), intento.estado(), intento.proximoIntento());
                } else {
                    var conteo = intento.recuperacion().incorporacion();
                    LOG.info("Sincronizacion: suministro {}; resultado {}; nuevas {}; repetidas {}; rechazadas {}; proximo intento UTC {}.",
                        intento.reserva().suministro().valor(), intento.estado(), conteo.nuevas(),
                        conteo.repetidas(), conteo.rechazadas(), intento.proximoIntento());
                }
                if (intento.error() != null) LOG.debug("Detalle de sincronizacion", intento.error());
            }
        } catch (RuntimeException error) {
            LOG.error("No se pudo ejecutar el ciclo de sincronizacion. Se volvera a intentar.", error);
        }
    }
}
