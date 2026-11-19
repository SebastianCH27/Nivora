package pe.nivora.backend.tarifas.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pe.nivora.backend.tarifas.aplicacion.SincronizarTarifas;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.tarifas.sincronizacion.habilitada", havingValue = "true")
public class SincronizacionTarifasAutomatica {
    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionTarifasAutomatica.class);
    private final SincronizarTarifas sincronizar;

    public SincronizacionTarifasAutomatica(SincronizarTarifas sincronizar) {
        this.sincronizar = sincronizar;
    }

    @Scheduled(initialDelayString = "${nivora.tarifas.sincronizacion.espera-inicial-ms}",
        fixedDelayString = "${nivora.tarifas.sincronizacion.intervalo-ms}")
    public void actualizar() {
        try {
            for (var intento : sincronizar.ejecutarCiclo()) {
                String codigo = intento.reserva().suministro().valor();
                if (!intento.programacionConfirmada()) {
                    LOG.warn("Sincronizacion de tarifas: suministro {}; resultado {}; reserva sin confirmar.",
                        codigo, intento.estado());
                } else if (intento.guardado() != null) {
                    LOG.info("Sincronizacion de tarifas: suministro {}; revision {}; proximo intento UTC {}.",
                        codigo, intento.guardado().revision(), intento.proximoIntento());
                } else {
                    LOG.warn("Sincronizacion de tarifas: suministro {}; resultado {}; proximo intento UTC {}.",
                        codigo, intento.estado(), intento.proximoIntento());
                }
                if (intento.error() != null) LOG.debug("Detalle de sincronizacion de tarifas", intento.error());
            }
        } catch (RuntimeException error) {
            LOG.warn("No se pudo ejecutar el ciclo de tarifas. Se volvera a intentar.");
            LOG.debug("Detalle del fallo del ciclo de tarifas", error);
        }
    }
}
