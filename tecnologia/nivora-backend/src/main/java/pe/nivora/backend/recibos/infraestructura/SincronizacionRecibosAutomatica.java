package pe.nivora.backend.recibos.infraestructura;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.aplicacion.SincronizarRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "nivora.recibos.sincronizacion.habilitada", havingValue = "true")
public class SincronizacionRecibosAutomatica {
    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionRecibosAutomatica.class);
    private final SincronizarRecibos sincronizar;

    public SincronizacionRecibosAutomatica(SincronizarRecibos sincronizar) {
        this.sincronizar = sincronizar;
    }

    @Bean
    static SincronizarRecibos sincronizarRecibos(RepositorioSincronizacionRecibos programacion,
            IncorporarRecibos incorporar, Clock reloj,
            @Value("${nivora.recibos.sincronizacion.maximo-intentos-por-ciclo}") int intentos,
            @Value("${nivora.recibos.sincronizacion.limite-recibos}") int limite,
            @Value("${nivora.recibos.sincronizacion.reserva-segundos}") long reserva,
            @Value("${nivora.recibos.sincronizacion.intervalo-completo-segundos}") long intervalo,
            @Value("${nivora.recibos.sincronizacion.reintento-inicial-segundos}") long inicial,
            @Value("${nivora.recibos.sincronizacion.reintento-maximo-segundos}") long maximo) {
        var politica = new SincronizarRecibos.Politica(intentos, limite, Duration.ofSeconds(reserva),
            Duration.ofSeconds(intervalo), Duration.ofSeconds(inicial), Duration.ofSeconds(maximo));
        return new SincronizarRecibos(programacion, incorporar, politica, reloj);
    }

    @Scheduled(initialDelayString = "${nivora.recibos.sincronizacion.espera-inicial-ms}",
        fixedDelayString = "${nivora.recibos.sincronizacion.intervalo-ms}")
    public void actualizar() {
        try {
            for (var intento : sincronizar.ejecutarCiclo()) {
                String codigo = intento.reserva().suministro().valor();
                if (!intento.programacionConfirmada()) {
                    LOG.warn("Sincronizacion de recibos: suministro {}; resultado {}; reserva sin confirmar.",
                        codigo, intento.estado());
                } else if (intento.incorporacion() != null) {
                    var conteo = intento.incorporacion();
                    LOG.info("Sincronizacion de recibos: suministro {}; nuevos {}; repetidos {}; proximo intento UTC {}.",
                        codigo, conteo.nuevos(), conteo.repetidos(), intento.proximoIntento());
                } else {
                    LOG.warn("Sincronizacion de recibos: suministro {}; resultado {}; proximo intento UTC {}.",
                        codigo, intento.estado(), intento.proximoIntento());
                }
                if (intento.error() != null) LOG.debug("Detalle de sincronizacion de recibos", intento.error());
            }
        } catch (RuntimeException error) {
            LOG.warn("No se pudo ejecutar el ciclo de recibos. Se volvera a intentar.");
            LOG.debug("Detalle del fallo del ciclo de recibos", error);
        }
    }
}
