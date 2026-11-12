package pe.nivora.backend.lecturas.infraestructura.sincronizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pe.nivora.backend.lecturas.aplicacion.SincronizarLecturas;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.notificaciones.aplicacion.EvaluarAlertaConsumo;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.sincronizacion.habilitada", havingValue = "true")
public class SincronizacionAutomatica {
    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionAutomatica.class);
    private final SincronizarLecturas sincronizar;
    private final EvaluarAlertaConsumo evaluarAlerta;
    private final boolean alertasHabilitadas;

    public SincronizacionAutomatica(SincronizarLecturas sincronizar, EvaluarAlertaConsumo evaluarAlerta,
            @Value("${nivora.alertas-consumo.habilitada}") boolean alertasHabilitadas) {
        this.sincronizar = sincronizar;
        this.evaluarAlerta = evaluarAlerta;
        this.alertasHabilitadas = alertasHabilitadas;
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
                if (alertasHabilitadas && intento.programacionConfirmada()
                        && intento.estado() == EstadoSincronizacion.RECORRIDO_COMPLETO) {
                    evaluarAlerta(intento);
                }
            }
        } catch (RuntimeException error) {
            LOG.error("No se pudo ejecutar el ciclo de sincronizacion. Se volvera a intentar.", error);
        }
    }

    private void evaluarAlerta(SincronizarLecturas.Intento intento) {
        var suministro = intento.reserva().suministro();
        try {
            var resultado = evaluarAlerta.ejecutar(suministro);
            if (resultado.estado() == EvaluarAlertaConsumo.Estado.GENERADA) {
                LOG.info("Alerta de consumo: suministro {}; periodo {}; resultado GENERADA.",
                    suministro.valor(), resultado.periodo());
            }
        } catch (RuntimeException error) {
            LOG.warn("No se pudo evaluar la alerta de consumo del suministro {}.", suministro.valor());
            LOG.debug("Detalle de la evaluacion de alerta de consumo", error);
        }
    }
}
